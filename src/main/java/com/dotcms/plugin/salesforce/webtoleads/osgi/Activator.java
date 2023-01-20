package com.dotcms.plugin.salesforce.webtoleads.osgi;



import com.dotcms.plugin.salesforce.webtoleads.actionlet.WebToLeads;
import com.dotmarketing.osgi.GenericBundleActivator;
import org.osgi.framework.BundleContext;


public class Activator extends GenericBundleActivator {





    @SuppressWarnings ("unchecked")
    public void start ( BundleContext context ) throws Exception {

        //Initializing services...
        initializeServices( context );



        //Registering the test Actionlet
        registerActionlet( context, new WebToLeads() );
    }


    public void stop ( BundleContext context ) throws Exception {


        
        unregisterActionlets();

    }

}
