
# 项目总结
## 技术栈

SpringBoot+Redis+MyBatisPlus+Mysql+Redisson+Ngixn

### 后端

Spring 相关：

1. Spring Boot 2.x
2. Spring MVC

数据存储层：

1. MySQL：存储数据
2. MyBatis Plus：数据访问框架

Redis 相关：

1. spring-data-redis：操作 Redis
2. Lettuce：操作 Redis 的高级客户端
3. Apache Commons Pool：用于实现 Redis 连接池
4. Redisson：基于 Redis 的分布式数据网格

工具库：

1. HuTool：工具库合集
2. Lombok：注解式代码生成工具

### 前端

前端不是本项目的重点，了解即可

1. 原生 HTML、CSS、JS 三件套
2. Vue 2（渐进式使用）
3. Element UI 组件库
4. axios 请求库

## 项目架构

手机或者app端发起请求，请求我们的nginx服务器，nginx基于七层模型走的是HTTP协议，可以实现基于Lua直接绕开tomcat访问redis，也可以作为静态资源服务器，轻松扛下上万并发， 负载均衡到下游tomcat服务器，打散流量，我们都知道一台4核8G的tomcat，在优化和处理简单业务的加持下，大不了就处理1000左右的并发， 经过nginx的负载均衡分流后，利用集群支撑起整个项目，同时nginx在部署了前端项目后，更是可以做到动静分离，进一步降低tomcat服务的压力，这些功能都得靠nginx起作用，所以nginx是整个项目中重要的一环。

在tomcat支撑起并发流量后，我们如果让tomcat直接去访问Mysql，根据经验Mysql企业级服务器只要上点并发，一般是16或32 核心cpu，32 或64G内存，像企业级mysql加上固态硬盘能够支撑的并发，大概就是4000起~7000左右，上万并发， 瞬间就会让Mysql服务器的cpu，硬盘全部打满，容易崩溃，所以我们在高并发场景下，会选择使用mysql集群，同时为了进一步降低Mysql的压力，同时增加访问的性能，我们也会加入Redis，同时使用Redis集群使得Redis对外提供更好的服务。

![1653059409865](https://niu.xzynet.com.cn/GitHub/1653059409865.png)

## 代码学习

- config 目录：存放项目依赖相关配置
  1. MvcConfig：配置了登录、自动刷新登录 Token 的拦截器
  2. MybatisConfig：配置 MyBatis Plus 分页插件
  3. RedissonConfig：创建单例 Redisson 客户端
  4. WebExceptionAdvice：全局响应拦截器

- controller 目录：存放 Restful 风格的 API 接口
- dto 目录：存放业务封装类，如 Result 通用响应封装（不推荐学习它的写法）
- entity 目录：存放和数据库对应的 Java POJO，一般是用 MyBatisX 等插件自动生成
- mapper 目录：存放操作数据库的代码，基本没有自定义 SQL，都是复用了 MyBatis Plus 的方法，不做重点学习。
- service 目录：存放业务逻辑处理代码，需要重点学习
  1. BlogServiceImpl：基于 Redis 实现点赞、按时间排序的点赞排行榜；基于 Redis 实现拉模式的 Feed 流，推荐学习
  2. FollowServiceImpl：基于 Redis 集合实现关注、共同关注，推荐学习
  3. ShopServiceImpl：基于 Redis 缓存优化店铺查询性能；基于 Redis GEO 实现附近店铺按距离排序，推荐学习
  4. UserServiceImpl：基于 Redis 实现短信登录（分布式 Session），推荐学习（虽然没有真的实现短信登录，而是通过日志打印验证码代替）
  5. VoucherOrderServiceImpl：基于 Redis 分布式锁、Redis + Lua 两种方式，结合消息队列，共同实现了秒杀和一人一单功能，非常值得学习
  6. VoucherServiceImpl：添加优惠券，并将库存保存在 Redis 中，为秒杀做准备。

- utils 目录：存放项目内通用的工具类，需要重点学习
  1. CacheClient：封装了通用的缓存工具类，涉及泛型、函数式编程等知识点，值得学习
  2. RedisConstants：保存项目中用到的 Redis 键、过期时间等常量，值得学习
  3. RedisIdWorker：基于 Redis 的全局唯一自增 id 生成器，值得学习
  4. RedisTokenInterceptor：自动刷新 Redis 登录 Token 有效期，值得学习
  5. SimpleRedisLock：简单的 Redis 锁实现，了解即可，一般用 Redisson
  6. UserHolder：线程内缓存用户信息，可以学习

## 简历写法

### 项目介绍

该项目是仿大众点评的在线店铺点评 APP（H5 网页），旨在为用户提供一个点评和分享平台。通过平台，用户可以浏览、搜索和比校各种商家的信息，包括美食、KTV等。用户可以在平台上创建个人账号、关注他人、发表评论、点赞、上传图片和发布自己的博客。

基于 Spring Boot + Redis 的店铺点评 APP，实现了找店铺 => 写点评 => 看热评 => 点赞关注 => 关注 Feed 流的完整业务流程。

### 主要工作

1. 短信登录：使用 Redis 实现分布式 Session，解决集群间登录态同步问题；使用 Hash 代替 String 来存储用户信息，节约了约20% 的内存并便于单字段的修改。（需要自己实际测试对比数据，节省内存的原因是不用保存序列化对象信息或者 JSON 的一些额外字符串）

2. 店铺查询：使用 Redis 对高频访问店铺进行缓存，降低 DB 压力同时提升 90% 的数据查询性能。

3. 为方便其他业务后续使用缓存，使用泛型 + 函数式编程实现了通用缓存访问静态方法，并解决了缓存雪崩、缓存穿透、缓存击穿等问题。

4. 使用常量类全局管理 Redis Key 前缀、TTL 等，保证了键空间的业务隔离，减少冲突。

5. 优惠券秒杀：使用 Redis + Lua 脚本实现库存预检，并通过 Stream 队列实现订单的异步创建，解决了超卖问题、实现一人一单。实现相比传统数据库，秒杀性能提高了 xx%。（需要自己实际测试对比数据）

6. 使用 Redis 的 Geo + Hash 数据结构分类存储附近商户，并使用 Geo Search 命令实现高性能商户查询及按距离排序。

7. 使用 Redis List 数据结构存储用户点赞信息，并基于 ZSet 实现 TopN 点赞排行，实测相对于 DB 查询性能提升 xx%。（需要自己实际测试对比数据）

8. 使用 Redis Set 数据结构实现用户关注、共同关注功能（交集），实测相对于 DB 查询性能提升 xx%。（需要自己实际测试对比数据）

9. 使用 Redis BitMap 实现用户连续签到统计功能，相对于传统关系库存储，节约 xx% 的内存并提升 xx% 的查询性能。（需要自己实际测试对比数据）

10. 在系统用户量不大的前提下，基于推模式实现关注 Feed 流，保证了新点评消息的及时可达，并减少用户访问的等待时间。

    



再列举一些该项目可以扩展的点，有能力的同学可以自己尝试实现**（注意，没有自己实现过千万别写到简历上！！！做没做过一问便知）**：

1. 使用 Redis + Token 机制实现单点登录（补充到上述第 1 点中）
2. 对 Redis 的所有 key 设置 N + n 的过期时间，从而合理使用内存并防止缓存雪崩；针对热点店铺缓存，使用逻辑过期（或自动续期）机制解决缓存击穿问题，防止数据库宕机。
3. 使用 Redis 的 Geo + Hash 数据结构分类存储附近商户，并使用 Geo Search 命令实现高性能商户查询及按距离排序，实测相对于传统 DB 查询 + 业务层计算的方式，性能提升 xx%。
4. 使用 Redis Set 数据结构实现用户关注、共同关注功能（交集），实测相对于 DB 查询性能提升 xx%，并使用 Redis AOF + 业务层日志防止关注数据丢失。（理解 AOF 和 RDB 持久化机制后再写这点）
5. 基于 Spring Scheduler 实现对热点数据的定期检测和缓存预加载，提升用户的访问体验，并通过 Redisson 分布式锁保证集群中同一时刻的定时任务只执行一次。
6. 关注 Feed 流可以改为推拉结合模式（活跃用户用推、普通用户用拉）
7. 使用哨兵集群来提升 Redis 的读并发量、可用性和稳定性；或者使用 Redis 分片集群来提升 Redis 读写并发量、总存储容量，保障可用性和稳定性。
8. 随着系统用户增多，使用 Redis HyperLogLog 代替 DB 来实现店铺和点评的 UV 统计，提高 xx% 的查询分析性能并解决 xx% 的内存空间。
