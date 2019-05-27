# UnityDependency
用来统一AndroidStudio项目各个第三方库的依赖版本的Intellij插件


## 使用
插件的入口在[Tools]->[Dependency]第一步创建依赖文件点击[Create config.gradle]

![](https://qqadapt.qpic.cn/txdocpic/0/6b90dd8010befd0e361bd1a5bc10a68e/0)

然后在弹窗的窗口里面选择是否使用自定义的json配置文件，因为插件里面内置了一套标准，可以直接点击[取消]。

![](https://qqadapt.qpic.cn/txdocpic/0/26c25a90cc2f53c7ffde4ad47e6263b6/0)

如果点击确定，则会弹出一个自定义json地址的弹窗,自定义的json格式需要和弹窗提示的一致。

![](https://qqadapt.qpic.cn/txdocpic/0/bf8da6183e8abc2e2009f1fb07f03a3b/0)

这些操作之后会在跟目录创建一个“dependencyConfig.gradle”的文件，类似下面这样：

![](https://qqadapt.qpic.cn/txdocpic/0/e3b8ee4ffea03bb41d1a9c1b15a542df/0)

第二步统一版本号点击[Unity Dependency]，然后就是见证奇迹的时刻了！

![](https://qqadapt.qpic.cn/txdocpic/0/25a73eb86c6dc981f1c635fe52c9b9a4/0)
修改后的"build.gradle"文件：              

![](https://qqadapt.qpic.cn/txdocpic/0/d7f61c34dbd8994c10dea04f8494a193/0)
![](https://qqadapt.qpic.cn/txdocpic/0/7dbae89e78065b55a0ff5012a080c497/0)
            
