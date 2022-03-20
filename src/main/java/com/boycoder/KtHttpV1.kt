package com.boycoder

import com.boycoder.annotations.Field
import com.boycoder.annotations.GET
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Proxy

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaMethod

data class Repo(
    var added_stars: String?,
    var avatars: List<String>?,
    var desc: String?,
    var forks: String?,
    var lang: String?,
    var repo: String?,
    var repo_link: String?,
    var stars: String?
)

data class RepoList(var count: Int?, var items: List<Repo>?, var msg: String?)

interface ApiService {
    @GET("/repo")
    fun repos(
        @Field("lang") lang: String,
        @Field("since") since: String
    ) : RepoList
}

object KtHttpV1 {

    private var okHttpClient:OkHttpClient = OkHttpClient()
    private var gson:Gson = Gson()
    var baseUrl = "https://trendings.herokuapp.com"
    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(service.classLoader, arrayOf<Class<*>>(service))
        { proxy, method, args ->
            val annotations = method.annotations
            for (annotation in annotations) {
                if (annotation is GET) {
                    val url = baseUrl + annotation.value
                    return@newProxyInstance invoke(url, method, args!!)
                }
            }
            return@newProxyInstance null
        } as T
    }

    private fun invoke(path: String, method: Method, args: Array<Any>): Any? {
        if (method.parameterAnnotations.size != args.size) return null
        var url = path
        // https://blog.csdn.net/qq_27397913/article/details/102475475
        // 二维数组，一个参数有多个注解[][]
        val parameterAnnotations = method.parameterAnnotations
        for (i in parameterAnnotations.indices) {
            for (parameterAnnotation in parameterAnnotations[i]) {
                println("parameterAnnotation = $parameterAnnotation")
                if (parameterAnnotation is Field) {
                    val key = parameterAnnotation.value;
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

        val response = okHttpClient.newCall(request).execute()

        val genericReturnType = method.genericReturnType
        val body = response.body
        val json = body?.string()
        val result = gson.fromJson<Any?>(json, genericReturnType)
        return result
    }

}

fun main() {
    val api: ApiService = KtHttpV1.create(ApiService::class.java)

    val data: RepoList = api.repos(lang = "Kotlin", since = "weekly")

    println(data)
}

class ApiImpl(val h: InvocationHandler) : Proxy(h), ApiService {
    override fun repos(lang: String, since: String): RepoList {
        val method: Method = ::repos.javaMethod!!
        val args = arrayOf(lang, since)
        return h.invoke(this, method, args) as RepoList
    }
}
