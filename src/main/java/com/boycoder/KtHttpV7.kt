package com.boycoder

import com.boycoder.annotations.Field
import com.boycoder.annotations.GET
import com.google.gson.Gson
import com.google.gson.internal.`$Gson$Types`.getRawType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import kotlin.coroutines.resumeWithException
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.KFunction
import kotlin.reflect.KClass
import kotlin.coroutines.*


// 代码段6

interface ApiServiceV7 {
    @GET("/repo")
    fun repos(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): KtCall<RepoList>

    @GET("/repo")
    fun reposSync(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): RepoList

    @GET("/repo")
    fun reposFlow(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): Flow<RepoList>

    @GET("/repo")
    // 1，挂起函数
    suspend fun reposSuspend(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): RepoList
}

object KtHttpV7 {
    private var okHttpClient: OkHttpClient = OkHttpClient()
    private var gson: Gson = Gson()
    private var baseUrl = "https://trendings.herokuapp.com"

    fun <T : Any> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf<Class<*>>(service)
        ) { proxy, method, args ->
            val annotations = method.annotations
            for (annotation in annotations) {
                if (annotation is GET) {
                    val url = baseUrl + annotation.value
                    return@newProxyInstance invoke<T>(url, method, args!!)
                }
            }
            return@newProxyInstance null

        } as T
    }

    private fun <T : Any> invoke(path: String, method: Method, args: Array<Any>): Any? {
        if (method.parameterAnnotations.size != args.size) return null
        println("invoke 111")
        var url = path
        val parameterAnnotations = method.parameterAnnotations
        for (i in parameterAnnotations.indices) {
            for (parameterAnnotation in parameterAnnotations[i]) {
                if (parameterAnnotation is Field) {
                    val key = parameterAnnotation.value
                    val value = args[i].toString()
                    if (!url.contains("?")) {
                        url += "?$key=$value"
                    } else {
                        url += "&$key=$value"
                    }

                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val call = okHttpClient.newCall(request)

        return when {
            isSuspend(method) -> {
                // 支持挂起函数
                val genericReturnType = method.kotlinFunction?.returnType?.javaType ?: throw IllegalStateException()
                val call = okHttpClient!!.newCall(request)

                val continuation = args.last() as? Continuation<T>

                val func = KtHttpV7::class.getGenericFunction("realCall2")
                // 反射调用realCall()
                func.call(this, call, gson, genericReturnType, continuation)
            }

            isKtCallReturn(method) -> {
                val genericReturnType = getTypeArgument(method)
                KtCall<T>(call, gson, genericReturnType)
            }
            isFlowReturn(method) -> {
                println("flows ")
                flow<T> {
//                    logX("Start in")
//                    val response = okHttpClient.newCall(request).execute()
//                    val genericReturnType = method.genericReturnType
//                    val json = response.body?.string()
//                    val result = gson.fromJson<T>(json, genericReturnType)
//                    logX("Start emit")
//                    emit(result)
//                    logX("End emit")

                    logX("Start in")
                    // 注意这里，需要取泛型参数的类型 实参
                    val genericReturnType = getTypeArgument(method)
                    println("genericReturnType = $genericReturnType, m = ${method.genericReturnType}")
                    val response = okHttpClient.newCall(request).execute()
                    val json = response.body?.string()
                    val result = gson.fromJson<T>(json, genericReturnType)
                    logX("Start emit")
                    emit(result)
                    logX("End emit")
                }
            }
            else -> {
                val response = this.okHttpClient.newCall(request).execute()
                val genericReturnType = method.genericReturnType
                val json = response.body?.string()
                gson.fromJson<T>(json, genericReturnType)
            }
        }
    }

    private fun isSuspend(method: Method): Boolean {
        return method.kotlinFunction?.isSuspend ?: false
    }

    // 4，真正执行网络请求的方法
    suspend fun <T : Any> realCall2(call: Call, gson: Gson, type: Type): T =
        suspendCancellableCoroutine { continuation ->
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    try {
                        val t = gson.fromJson<T>(response.body?.string(), type)
                        continuation.resume(t)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })

            continuation.invokeOnCancellation {
                call.cancel()
            }
        }

    private fun logX(s: String) {
        println(Thread.currentThread().name)
    }

    // 2，获取方法的反射对象
    fun KClass<*>.getGenericFunction(name: String): KFunction<*> {
        return members.single { it.name == name } as KFunction<*>
    }

    fun isFlowReturn(method: Method) =
        getRawType(method.genericReturnType) == Flow::class.java

    fun getTypeArgument(method: Method) =
        (method.genericReturnType as ParameterizedType).actualTypeArguments[0]

    fun isKtCallReturn(method: Method) =
        getRawType(method.genericReturnType) == KtCall::class.java
}

fun main() = runBlocking {
    val data: RepoList = KtHttpV7.create(ApiServiceV7::class.java).reposSuspend(
        lang = "Kotlin",
        since = "weekly"
    )

    println(data)
}
/*
输出结果
正常
*/


