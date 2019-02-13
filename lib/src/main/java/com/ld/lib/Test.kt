package com.ld.lib

import com.sun.xml.internal.bind.v2.TODO

class Test() {
    lateinit var aI: A;
    public fun setAInterface(a: A) {
        this.aI = a
    }

    public fun alreadySet() {
        setAInterface(object : A {
            override fun haha() {

            }
        })
    }
}

interface A {
    fun haha();
}