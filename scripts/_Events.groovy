eventConfigureTomcat = { tomcat ->
    String contextFile = "web-app/WEB-INF/context.xml"
    System.getProperties().list(System.out);

    //increase max cookie size
    tomcat.connector.setAttribute("port",Integer.parseInt(System.getProperty("server.port")))
    tomcat.connector.setAttribute("maxHttpHeaderSize",262144)
}