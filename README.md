# DIY Docker It Yourself
## 技术选型
因为感觉写flask容易写飘（指随心所欲的弱类型，虽然写强类型也行  
起spring又太重了，所以选了ktor，尽管之前只写过demo

主要依赖项是netty和kotlin的官方json库

## 设计模式
* Main.kt
服务主入口，分离了模块函数和路由函数
1. 模块函数
```kotlin
fun Application.module()
```
2. 路由函数 
* blobsRoutes管理  
/v2/{name}/blobs/{digest}，/v2/{name}/blobs/uploads，/v2/{name}/blobs/uploads/{uuid}等路由
```kotlin
fun Route.blobsRoutes(
    blobService: BlobService
)
```
* manifestsRoutes管理
```kotlin
fun Route.manifestsRoutes(
    manifestService: ManifestService
)
```

## 扩展功能

## 测试

## 部署
1.环境
找个debian

2.打包
用ktor官方的打包方法，直接包成fatjar包，虽然可能臃肿了一些，但是放到linux上跨平台也没问题

## 其他
1. 这个年代说自己写代码不用AI怕是别人都不信，这个项目期间只问了gpt一些代码思路，写的时候都是手敲的  
2. 本来想用redis存session，但是感觉太麻烦了没必要，就用Map简写了
3. gc和认证并没有做