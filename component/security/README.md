# 安全验证模块

    感觉这个名字不太贴合实际 , 应该叫做接口权限验证会更好一点 , 或者叫做基于 JWT 的接口权限验证最好
    
    作者的职业生涯中调研并使用过 SpringSecurity和Shiro , 以前的 JSP 时代没的说 , 基于 JSTL 标签 , 效果杠杠的
    可现在前后端分离 , 我验证一下接口的权限就有点 , 怎么说呢 , 不伦不类 , Shiro 用的少 , 没有话语权 , 说一下 SpringSecurity 吧
    这玩意 , 市面上的文档和案例少的一匹 , 官方说明说吧 , 嘿嘿 , 英文不好 , 也没那么耐心看 .
    以前别人搭过这玩意 , 感觉也没啥 , 真自己搞一下子吧 , 发现完全不是那么回事 , 仔细看看 SpringSecurity 家族里面的东西 , 真的海了去了 .
    尤其是 OAuth2.0 这玩意 , 人家那单点登录真的是个重量级的玩意 , 针对多个系统 , 多个用户权限的时候用的玩意 , 可咱们也就用个 JWT 这种操作 , 
    说实在的用 SpringSecurity 很浪费 , 不是它不好 , 是我不行(男人不可以说不行) , 
    在此做一下反省 , 1: SpringSecurity 实在是没玩明白 2: 这玩意确实很重
    扯远了 , 接着说一下一个较为普通的微服务架构的安全验证 , 作者的期望结果是怎样的 , 然后再看一下作者找到的一些实现方案是怎么样的 , 最后讲一下作者是怎么实现的 
    以下为具体流程: 
    1: 前端发起一个 HTTP 请求
    2: 这个请求被网关拦截到
    3: 网关从这个请求的请求头中得到一个 TOKEN , 然后解密这个 TOKEN 
    4: TOKEN 解密后得到用户的必要信息和权限信息
    5: 判断这个 TOKEN 中的权限是否拥有访问这个接口的权限 , 若没有权限则直接抛出无权限异常 , 若有权限则放行
    6: 将 TOKEN 解密后的信息直接传递给具体要访问的服务 
    7: 后面的服务获取到解密后的信息进行使用
    8: 在实际使用的时候我们的 Controller 方法只需要添加一个注解 , 并标注清楚这个接口可以被哪些权限访问即可 
    而作者在尝试使用 SpringSecurity 的时候发现 , 有一下几个问题
    1: 某些做法是创建一个服务 , 然后登陆的时候把用户的信息存储到 内存,Redis,MySQL 等地方 , 然后返回一个较为简短的 TOKEN , 处女座的朋友应该会发现这样做是极度恶心的 , 
       因为每有一次请求 , 我就要拿着这个 TOKEN 去各种介质中查找用户的信息 , 耗不耗时的先不说 , 多依赖一种东西 , 不稳定因素总会出现吧 , 开发成本会增加吧 , 维护成本也会增加 , 在集群部署的时候也呵呵 ,
       最痛苦支出在于这个服务可以暴露出几个端口(也就是 url) , 登陆的时候调用登陆端口 , 然后有一个验证端口 , 需要配置到网关 , 然后就是网关每次收到一个请求 , 先请求这个安全服务进行验证 , 然后... , 反正我是接受不了这种做法
    2: 另一种较为聪明的做法 , 把权限信息直接加密到 TOKEN 中去 , 这种做法也需要创建一个用户授权服务 , 也会跟 1 中一样每个请求网关都会到这个服务进行验证 , shit
    3: 以上两种做法都有一个问题就是 , 在具体访问到的那个服务中也要配置一个安全服务的端口 , 用于获取当前用户的基础信息 , shit...
    4: 还有一些其它乱七八糟的做法 , 大概就是这些不爽了 , 这些已经足够不爽了
    说一下我要解决的一个问题
    1: 用户状态信息的存储仅依靠 JWT 的 TOKEN , 正是因为后端人员不想管理用户信息 , 所以才从 Session 转而使用 JWT , 现在后端还要维护这些东西 , 不合理
    2: TOKEN 的解密只在网关进行一次 , 解密 TOKEN 是必不可少的 , 但是要知道解密是较为耗费时间的 , 当然这个时间是可以接受的 , 但是却不是可以随意浪费的 , 尤其是非对称加密比对称加密的空间复杂度要高很多 , 尤其是在 TOKEN 较长的情况下 , 耗费的时间会更多
    3: 直接在 Controller 的方法上用一个注解 , 然后写清楚访问这个接口的权限即可
    4: 网关不需要在运行状态下不断的请求其它服务进行权限的验证
    其实就这些 , 只要做到这些我想要的东西就有了
    以下说一下代码吧
    实际开发完成之后产生了三个模块
    security-core: security 模块的核心代码包 , 用于放置一些通用类或常量等
    security-provider: 被业务服务所依赖 , 用于获取业务服务需要权限验证的接口等功能
    security-consumer: 被网关服务所依赖(仅支持 spring-cloud-gateway) , 用于进行权限的验证
    以上模块的名字起的反正是挺恶心的 , 以后有时间改一下 
    
## 核心代码 [security-core]

    这个模块下有三个类 , 一个注解 , 一个包装类 , 一个常量类
    Authority: 这是一个注解 , 该注解使用的对象为被 RequestMapping,GetMapping,PostMapping 等注解标注的接口 , 标注在其它方法上无效
               参数: 
                    role: 字符串 , 用于描述访问该接口所需要的角色标识 , 这些标识应已经录入到数据库中(uaa 的数据库) , 且在拥有这些角色的用户的 TOKEN 中应包含这些信息
                          若有多个角色可以访问这个接口 , 那么以英文的逗号进行分割
                    authority: 字符串 , 用于描述访问该接口所需要的权限标识 , 这些标识应已经录入到数据库中(uaa 的数据库) , 且在拥有这些权限的用户的 TOKEN 中应包含这些信息
                               若有多个权限可以访问这个接口 , 那么以英文的逗号进行分割
                    description: 字符串 , 描述信息 , 这个描述信息所描述的应为这个接口而不是其所需要的角色或权限
                    isLogin: 布尔类型 , 用于设置这个接口是否需要登录才能够访问 , 默认为 true , 如 登录,注册 这类的接口就需要将该属性设置为 false , 一旦该属性被设置为 false , 
                              那么其它的属性设置均无效 , 也就是说当该属性为 false 时 , 这个接口不需要任何权限即可访问
                    isHaveAuthority: 布尔类型 , 用于设置这个接口是否需要拥有权限才需要访问 , 默认为 true , 如 运营管理后台的首页这类的接口 , 一般情况是只需要登录即可被访问 , 
                                     此时可以设置该属性为 false
    AuthorityWrapper: 一个包装类 , 用于包装业务服务中的接口信息 , 这些接口信息会缓存到网关服务的内存中 
    Constants: 里面定义类三个常量 , 网关在 eureka 上的前缀,网关暴露出来的端点,业务服务暴露出来的端点
    
### 业务服务依赖 [security-provider]

    业务服务 , 即向外暴露接口的服务 , 需要依赖该模块 , 具体如下: 
    1: 服务启动的时候自动收集当前服务中如 RequestMapping,GetMapping,PostMappting 这种注解标注的方法的信息 , 
       这些方法也就是常说的接口 , 每一个方法都应该有  在当前项目的访问url,该方法的请求类型(GET,POST,PUT等),Authority注解中的参数信息 , 需要注意的一点是 , 若一个接口未
       被 Authority 注解所标注 , 那这个接口是不会被网关转发过来的 , 网关会认为这是一个错误的请求 , 但这仅仅是针对网关来说的 , Feign 的调用不经过网关 , 所以不会受到影响 .
    2: 将 1 收集到的信息推送到服务所暴露出来的端点(在讲到 security-consumer 的时候会进行详细描述) , 若网关为集群部署则会循环推送 , 但需要注意的一点是 , 推送操作只会进行一次 , 
       若推送不成功则会抛出异常 , 但不会重复尝试推送
    3: 提供一个向外暴露的端点 , 以便网关服务用于主动获取本服务的接口信息
    4: 1 收集到的接口的信息不会缓存到内存中 , 只有在推送和被网关请求获取时才会进行一次查询 , 后面会被垃圾回收
    
### 网关服务依赖 [security-consumer]

    网关服务 , 目前仅支持 gateway , 需要依赖该模块 , 具体如下: 
    1: 网关服务在启动的时候首先从自己的配置文件中获取需要转发的服务名称
    2: 通过 1 得到的全部名称到注册中心中得到这些服务的地址
    3: 依靠这些地址去访问这些服务向外暴露的请求接口权限信息的接口从而得到该服务的接口权限信息 , 这种操作仅请求一个服务 , 即使这个服务是集群部署
       若服务接口信息有所更新 , 则需要服务重启并上报给网关 , 否则网关缓存的数据不会得到更新 , 网关也不会定时去拉取业务服务的接口信息
    4: 网关在接收到请求之后首先拆分 url , 通过 url 中的 app 的名称找到缓存中该 app 的接口信息 , 然后通过 url 找到这个具体这个接口的权限信息(AuthorityWrapper)
    5: 然后判断该接口是否需要登录状态 , 若不需要则不再进行其它操作 , 直接进行转发
    6: 若需要登录状态则去获取 TOKEN , 并解密 TOKEN , 也就是说 , 若该接口不需要登录状态 , 则业务服务中是无法获取到登录用户的信息的 , 因为网关不会解密 TOKEN
    7: 接下来会判断该接口是否需要权限 , 若不需要权限则直接进行转发
    8: 若需要权限则会判断该用户是否拥有访问该接口的角色和权限 , 若拥有则进行转发 
    9: 以上条件若不匹配则会响应 400 异常
    10: 网关也会提供一个刷新接口缓存的端点 , 以便业务服务可以访问该接口进行更新接口权限信息的操作
    
## 注

    作者认为在一个项目需求确定下来之后 , 其所有的权限都应该已经被确定下来(不包括数据权限) ,
    这些权限应该首先会被录入到 uaa 服务的权限表中 , 并定义好其 mark ,
    
    若一个用户拥有的角色和权限过多 , 可能会导致其生成的 TOKEN 过长 , 所以开发人员在定义权限和角色的 mark 的时候应该尽量的简短一些

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    