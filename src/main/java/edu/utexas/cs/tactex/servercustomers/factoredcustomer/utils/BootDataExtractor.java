/*
 * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
 * Copyright (c) 2013-2016 Daniel Urieli and Peter Stone {urieli,pstone}@cs.utexas.edu               
 *
 *
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.utexas.cs.tactex.servercustomers.factoredcustomer.utils;

import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

/**
 * @author Prashant Reddy
 */
public class BootDataExtractor
{
    static String inFile = "../server-main/bootstrap.xml";
    static String outFile = "../factored-customer/fcm-bootdata.csv";
    		
    public static void main(String[] args) 
    {
        try {
            String in = inFile;
            if (args.length > 0) in = args[0];
            String out = outFile;
            if (args.length > 1) in = args[1];
            
            System.out.println("Input: " + in);
            
            InputStream inStream = new FileInputStream(in);
            DataOutputStream outStream = new DataOutputStream(new FileOutputStream(out));
            
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(inStream);

            Element bootstrap = (Element) doc.getElementsByTagName("bootstrap").item(0);
            NodeList nodes = bootstrap.getElementsByTagName("customer-bootstrap-data");
            for (int i=0; i < nodes.getLength(); ++i) {
                Element csd = (Element) nodes.item(i);
                String name = csd.getAttribute("customerName");
                String type = csd.getAttribute("powerType");
                String data = csd.getElementsByTagName("netUsage").item(0).getTextContent();
                
                outStream.writeBytes(name + "," + type + "," + data + "\r\n");
            }
            outStream.close();
            inStream.close();
            
            System.out.println("Output: " + out);
        } 
        catch (Exception e) {
            System.err.println("Caught exception: " + e.toString());
            e.printStackTrace();
        }
    }
    
} // end class
