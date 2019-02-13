package com.ld.buildsrc

import com.android.SdkConstants
import com.android.build.api.transform.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.builder.core.AndroidBuilder
import com.android.ddmlib.Log
import com.android.utils.FileUtils
import groovyjarjarantlr.build.ANTLR.jarName
import javassist.ClassPool
import javassist.CtClass
import javassist.CtField
import javassist.CtMethod
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.filewatch.FileWatcherEvent.modify
import org.gradle.internal.impldep.bsh.commands.dir
import java.io.File
import java.io.IOException
import java.util.function.Consumer
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata.file
import java.io.FileOutputStream
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry


const val TAG = "TransformKotlin"

class TransformKotlin(val project: Project, val extension: BaseExtension) : Transform() {
    private val CLICK_LISTENER = "android.view.View\$OnClickListener"

    private val NEED_MODIFY_JAR_CLASS_NAME = setOf("com.ld.plugindemo","com.ld.lib")

    private var pool = ClassPool.getDefault()

    override fun getName(): String {
        return "刘东TestTransform"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        project.logger.log(LogLevel.ERROR, "进入transform方法")

        if (transformInvocation == null) {
            return
        }
        extension.bootClasspath.forEach {
            pool.insertClassPath(it.toString())
        }
        pool.importPackage("android.os.Bundle");
        transformInvocation.outputProvider.deleteAll()
        transformInvocation.inputs.forEach {

            it.jarInputs.forEach { jarInput ->
                pool.appendClassPath(jarInput.file.absolutePath)

                // 重命名输出文件（同目录copyFile会冲突）
                var jarName = jarInput.name
                val md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length - 4)
                }
                val dest = transformInvocation.outputProvider.getContentLocation(
                    jarName + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR
                )
                var modifiedJar: File? = null
                if (isJarNeedModify(jarInput.file)) {

                    modifiedJar = modifyJarFile(jarInput.file, transformInvocation.context.getTemporaryDir())
                }
                if (modifiedJar == null) {
                    modifiedJar = jarInput.file
                }

                FileUtils.copyFile(modifiedJar, dest)
            }


            it.directoryInputs.forEach { dirInput ->
                val preFileName = dirInput.file.absolutePath
                pool.appendClassPath(preFileName)

                findTarget(dirInput.file, preFileName)

                // 获取output目录
                val dest = transformInvocation.outputProvider.getContentLocation(
                    dirInput.name,
                    dirInput.contentTypes,
                    dirInput.scopes,
                    Format.DIRECTORY
                )

                println("copy directory: " + dirInput.file.absolutePath)
                println("dest directory: " + dest.absolutePath)
                // 将input的目录复制到output指定目录
                FileUtils.copyDirectory(dirInput.file, dest)
            }
        }

    }


    /**
     * 植入代码
     * @param buildDir 是项目的build class目录,就是我们需要注入的class所在地
     * @param lib 这个是hackdex的目录,就是AntilazyLoad类的class文件所在地
     */
    public fun modifyJarFile(jarFile: File, tempDir: File): File? {
        /** 设置输出到的jar */
        var hexName = DigestUtils.md5Hex(jarFile.absolutePath).substring(0, 8);
        var optJar = File(tempDir, hexName + jarFile.name)
        var jarOutputStream = JarOutputStream(FileOutputStream(optJar));
        /**
         * 读取原jar
         */
        var file = JarFile(jarFile);
        var enumeration = file.entries();
        while (enumeration.hasMoreElements()) {
            var jarEntry = enumeration.nextElement();
            var inputStream = file.getInputStream(jarEntry);

            var entryName = jarEntry.getName();


            var className = ""

            var zipEntry = ZipEntry(entryName);

            jarOutputStream.putNextEntry(zipEntry);

            var modifiedClassBytes: ByteArray? = null;
            var sourceClassBytes = IOUtils.toByteArray(inputStream);
//            修改jar文件中的class
            if (entryName.endsWith(".class")) {
                className = entryName.replace(File.separator, ".").replace(".class", "")
                if (shouldModifyClass(className)) {
                    var ctClass = pool.get(className)
                    var hasMethod = ctClass.getDeclaredMethod("logTest")
                    if (hasMethod != null) {
                        ctClass.defrost()
                        val codeBody = """
                            System.out.println("动态添加的额外输出");
                """.trimIndent()
                        hasMethod.insertAfter(codeBody)
                        modifiedClassBytes = ctClass.toBytecode()
                        ctClass.detach()
                        println("write file: " + entryName + "\\" + ctClass.name)
                    }

                }
            }
            if (modifiedClassBytes == null) {
                jarOutputStream.write(sourceClassBytes);
            } else {
                jarOutputStream.write(modifiedClassBytes);
            }
            jarOutputStream.closeEntry();
        }
        jarOutputStream.close();
        file.close();
        return optJar;
    }

    private fun findTarget(dir: File, fileName: String) {
        if (dir.isDirectory()) {
            dir.listFiles().forEach {
                findTarget(it, fileName)
            }
        } else {
            modify(dir, fileName)
        }
    }

    private fun modify(dir: File, fileName: String): Unit {
        val filePath = dir.absolutePath

        if (!filePath.endsWith(SdkConstants.DOT_CLASS)) {
            return
        }
        if (filePath.contains("R$") || filePath.contains("R.class")
            || filePath.contains("BuildConfig.class")
        ) {
            return
        }

        val className = filePath.replace(fileName, "")
            .replace("\\", ".")
            .replace("/", ".")
        val name = className.replace(SdkConstants.DOT_CLASS, "")
            .substring(1)

        val ctClass = pool.get(name)
        val interfaces = ctClass.getInterfaces()
        var needToHandle  = ctClass.name.equals("com.ld.plugindemo.Main2Activity")
        log(ctClass.name + "需要处理：" + needToHandle)
        if (needToHandle) {
            project.logger.log(LogLevel.ERROR,"类名："+name)
//            修改自己项目中的class文件。
            var hasMethod = ctClass.getDeclaredMethod("handleSomething")
            if (hasMethod != null) {
                ctClass.defrost()
                val codeBody = """
                             view.setText("我是动态植入的");
                """.trimIndent()
                hasMethod.insertAfter(codeBody)
                ctClass.writeFile(fileName)
                ctClass.detach()
                println("write file: " + fileName + "\\" + ctClass.name)
                println("modify method: " + hasMethod.name + " succeed")
            }
            return
//            下面的是处理实现的某些接口，如果是匿名内部类时的一些操作。
            if (name.contains("\$")) {
                println("class is inner class：" + ctClass.name)
                println("CtClass: " + ctClass)
                val outer: CtClass = pool.get(name.substring(0, name.indexOf("\$")))
                val fields = ctClass.getFields()
                val field = fields.find {
                    it.type == outer
                }
                if (field != null) { //这个field很有可能结果就是this$0
                    println("fieldStr: " + field.name)
                    val body = "android.widget.Toast.makeText(" + field.name + "," +
                            "\"javassist\", android.widget.Toast.LENGTH_SHORT).show();"
                    addCode(ctClass, body, fileName)
                }
            } else {
                println("class is outer class: " + ctClass.name)
                //更改onClick函数
                val body =
                    "android.widget.Toast.makeText(\$1.getContext(), \"javassist\", android.widget.Toast.LENGTH_SHORT).show();"
                addCode(ctClass, body, fileName)
            }
        }
    }

    /**
     * 该jar文件是否包含需要修改的类
     * @param jarFile
     * @return
     */
    public fun isJarNeedModify(jarFile: File): Boolean {
        var modified = false;
        /**
         * 读取原jar
         */
        var file = JarFile(jarFile);
        var enumeration: Enumeration<JarEntry> = file.entries();
        while (enumeration.hasMoreElements()) {
            var jarEntry: JarEntry = enumeration.nextElement();
            var entryName = jarEntry.getName();
            var className: String;
            if (entryName.endsWith(".class")) {
                className = entryName.replace("/", ".").replace(".class", "")
                if (shouldModifyClass(className)) {
                    modified = true;
                    break;
                }
            }
        }
        file.close();
        return modified;
    }

    /**
     * 只扫描特定包下的类
     * @param className 形如 android.app.Fragment 的类名
     * @return
     */
    private fun shouldModifyClass(className: String): Boolean {
        NEED_MODIFY_JAR_CLASS_NAME.forEach {
            if (className.contains(it) && className.equals("com.ld.lib.Demo2")) {
                return (!className.contains("R\$") && !className.endsWith("R") && !className.endsWith("BuildConfig"))
            }
        }
        return false
    }


    private fun addCode(ctClass: CtClass, body: String, fileName: String) {

        ctClass.defrost()
        val method = ctClass.getDeclaredMethod("onClick", arrayOf(pool.get("android.view.View")))
        method.insertAfter(body)

        ctClass.writeFile(fileName)
        ctClass.detach()
        println("write file: " + fileName + "\\" + ctClass.name)
        println("modify method: " + method.name + " succeed")
    }

    private fun log(msg: String) {
        project.logger.log(LogLevel.ERROR, msg)
    }


}