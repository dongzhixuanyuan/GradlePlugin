package com.ld.plugindemo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_third_avtivity.*

class ThirdAvtivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_third_avtivity)
        tv_third.setOnClickListener {
            Log.d("dllllldd","fffff")
            Toast.makeText(this@ThirdAvtivity,"我是刘东",Toast.LENGTH_LONG).show()
        }
    }
}
