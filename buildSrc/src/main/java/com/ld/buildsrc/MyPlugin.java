package com.ld.buildsrc;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;


public class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        System.out.println("来自于buildSrc插件");
        AppExtension appExtension = project.getExtensions().getByType(AppExtension.class);
        appExtension.registerTransform(new TransformKotlin(project,appExtension));
    }
}
