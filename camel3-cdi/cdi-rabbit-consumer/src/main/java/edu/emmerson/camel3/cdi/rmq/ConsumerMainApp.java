package edu.emmerson.camel3.cdi.rmq;

import org.apache.camel.cdi.Main;

import java.util.jar.Manifest;

public class ConsumerMainApp {
	
    public static void main(String... args) throws Exception {
    	
    	Manifest mf = new Manifest(ConsumerMainApp.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
        System.out.println(mf.getMainAttributes().getValue("Main-Class"));
        System.out.println(mf.getMainAttributes().getValue("Class-Path"));
        
    	System.out.println("------------------- Starting with shared main library -------------------");
    	
//    	System.setProperty(JmxSystemPropertyKeys.DISABLED, "false");
    	
        Main main = new Main();

        main.run(args);
        
        
        //main.close();
    }
    
}
