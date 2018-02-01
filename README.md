# fabric-sample-java-sdk
fabric-sample  java sdk example, java FX
配合官方java sdk使用，版本号1.0.1
启动网络 
cd fabric-sdk-java/src/test/fixture/sdkintegration
./fabric up

由于本项目尚未完全完成，暂时只支持相对固定顺序使用，3a311538402d84d345c2d1e911367f86e9315d7f版本暂时可用，后续会出release版本
启动java fx项目
使用idea打开，适配好各项配置之后，用maven导入需要的包，运行打开图形界面

1.	初次使用，先点击join按钮，初始化channel和证书，然后installChaincode， 然后可以分别点击invoke和query
2.	在环境已配好channel的情况下，先点击connect连接，然后再点击invoke和query
