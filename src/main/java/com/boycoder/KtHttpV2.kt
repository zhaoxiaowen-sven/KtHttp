package com.boycoder

import com.boycoder.annotations.Field
import com.boycoder.annotations.GET
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Method
import java.lang.reflect.Proxy

//data class Repo(
//    var added_stars: String?,
//    var avatars: List<String>?,
//    var desc: String?,
//    var forks: String?,
//    var lang: String?,
//    var repo: String?,
//    var repo_link: String?,
//    var stars: String?
//)
//
//data class RepoList(var count: Int?, var items: List<Repo>?, var msg: String?)

//interface ApiService {
//    @GET("/repo")
//    fun repos(
//        @Field("lang") lang: String,
//        @Field("since") since: String
//    ) : RepoList
//}

object KtHttpV2 {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient()
    }
    private val gson: Gson by lazy {
        Gson()
    }
    var baseUrl = "https://trendings.herokuapp.com"

    inline fun <reified T> create(): T {
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java))
        { proxy, method, args ->
            val annotations = method.annotations
            return@newProxyInstance method.annotations
                .filterIsInstance<GET>()
                .takeIf { it.size == 1 }
                ?.let { invoke("$baseUrl${it[0].value}", method, args) }
        } as T
    }

    private fun parseUrl(acc: String, pair: Pair<Array<Annotation>, Any>) =
        //  取field 注解
        pair.first.filterIsInstance<Field>()
            .first()
            .let { field ->
                if (acc.contains("?")) {
                    println("first = ${pair.first}")
                    println("second = ${pair.second}")
                    "$acc&${field.value}=${pair.second}"
                } else {
                    "$acc?${field.value}=${pair.second}"
                }
            }

    fun invoke(url: String, method: Method, args: Array<Any>): Any? {
        // 二维数组
        return method.parameterAnnotations
            .takeIf { method.parameterAnnotations.size == args.size }
            ?.mapIndexed { index, it ->
                // 方法的 参数注解 和 参数值
                println("$it = ${args[index].toString()}")
                Pair(it, args[index])
            }
            ?.fold(url, ::parseUrl)
            ?.let { Request.Builder().url(it).build() }
            ?.let { okHttpClient.newCall(it).execute().body?.string() }
            ?.let { gson.fromJson(it, method.genericReturnType) }
    }
}

fun main() {
    val data: RepoList = KtHttpV2.create<ApiService>().repos(lang = "Kotlin", since = "weekly")
    println(data)
}
