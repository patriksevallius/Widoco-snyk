/*
 * Copyright 2012-2013 Ontology Engineering Group, Universidad Politécnica de Madrid, Spain
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package widoco;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.util.FileManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Some useful methods reused across different classes
 * @author Daniel Garijo
 */
public class WidocoUtils {
    /**
     * Method that will download the ontology to document with Widoco.
     * @param c Widoco configuration object.
     */
    public static void loadModelToDocument(Configuration c){
        OntModel model = ModelFactory.createOntologyModel();//ModelFactory.createDefaultModel();
        if(!c.isFromFile()){
            String newOntologyPath = c.getTmpFile().getAbsolutePath()+File.separator+"Ontology";
            downloadOntology(c.getOntologyURI(), newOntologyPath);
            c.setFromFile(true);
            c.setOntologyPath(newOntologyPath);
        }
        readModel(model, c);
        c.getMainOntology().setMainModel(model);
    }
    
    /**
     * Method that will download an ontology given its URI, doing content negotiation
     * The ontology will be downloaded in the first serialization available
     * (see Constants.POSSIBLE_VOCAB_SERIALIZATIONS)
     * @param uri the URI of the ontology
     * @param downloadPath path where the ontology will be saved locally.
     */
    public static void downloadOntology(String uri, String downloadPath){
        for(String serialization: Constants.POSSIBLE_VOCAB_SERIALIZATIONS){
                System.out.println("Attempting to download vocabulary in "+serialization);
                try{
                    URL url = new URL(uri);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("Accept", serialization);
                    int status = connection.getResponseCode();
                    boolean redirect = false;
                    if(status != HttpURLConnection.HTTP_OK){
                        if (status == HttpURLConnection.HTTP_MOVED_TEMP
			|| status == HttpURLConnection.HTTP_MOVED_PERM
				|| status == HttpURLConnection.HTTP_SEE_OTHER)
                            redirect = true;                        
                    }
                    //there are some vocabularies with multiple redirections:
                    //301 -> 303 -> owl
                    while(redirect){
                        String newUrl = connection.getHeaderField("Location");
                        connection = (HttpURLConnection) new URL(newUrl).openConnection();
                        connection.setRequestProperty("Accept", serialization);
                        status = connection.getResponseCode();
                        if(status != HttpURLConnection.HTTP_MOVED_TEMP && 
                                status != HttpURLConnection.HTTP_MOVED_PERM && 
                                status != HttpURLConnection.HTTP_SEE_OTHER)
                            redirect=false;
                    }
                    InputStream in = (InputStream) connection.getInputStream();
                    Files.copy(in, Paths.get(downloadPath), StandardCopyOption.REPLACE_EXISTING);
                    in.close();
                    break; //if the vocabulary is downloaded, then we don't download it for the other serializations
                }catch(Exception e){
                    System.err.println("Failed to download vocabulary in "+serialization);
                }
            }
    }
    
    /**
     * Method that reads a local file and loads it into the configuration.
     * @param model
     * @param ontoPath
     * @param ontoURL 
     */
    private static void readModel(OntModel model,Configuration c){
        String[] serializations = {"RDF/XML", "TTL", "N3"};
        String ontoPath = c.getOntologyPath();
        String ext = "";
        for(String s:serializations){
            InputStream in;
            try{
                in = FileManager.get().open(ontoPath);
                if (in == null) {
                    System.err.println("Error: Ontology file not found");
                    return;
                }
                model.read(in, null, s);
                System.out.println("Vocab loaded in "+s);
                if(s.equals("RDF/XML")){
                    ext="xml";
                }else if(s.equals("TTL")){
                    ext="ttl";
                }else if(s.equals("N3")){
                    ext="n3";
                }
                c.getMainOntology().addSerialization(s, "ontology."+ext);
                //c.setVocabSerialization(s);
                break;
            }catch(Exception e){
                System.err.println("Could not open the ontology in "+s);
            }
        }
        
    }
    
    public static void copyResourceFolder(String[] resources, String savePath) throws IOException{
        for (String resource : resources) {
            String aux = resource.substring(resource.lastIndexOf("/") + 1, resource.length());
            File b = new File(savePath+File.separator+aux);
            b.createNewFile();
            copyLocalResource(resource, b);
        }
    }
    
    /**
     * Method used to copy the local files: styles, images, etc.
     * @param resourceName Name of the resource
     * @param dest file where we should copy it. 
     */
    public static void copyLocalResource(String resourceName, File dest)  {
        try{
            copy(CreateResources.class.getResourceAsStream(resourceName), dest);
        }catch(Exception e){
            System.out.println("Exception while copying "+resourceName+" - "+e.getMessage());
        }
    }
    
    /**
     * Copy a file from outside the project into the desired file.
     * @param path
     * @param dest 
     */
    public static void copyExternalResource(String path, File dest) {
        try{
            InputStream is = new FileInputStream(path);
            copy(is, dest);
        }catch(Exception e){
            System.err.println("Exception while copying "+path+e.getMessage());
        }
    }
    
    /**
     * Code to unzip a file. Inspired from
     * http://www.mkyong.com/java/how-to-decompress-files-from-a-zip-file/
     * Taken from 
     * @param resourceName
     * @param outputFolder 
     */
    public static void unZipIt(String resourceName, String outputFolder){
 
     byte[] buffer = new byte[1024];
 
     try{
    	ZipInputStream zis = 
    		new ZipInputStream(CreateResources.class.getResourceAsStream(resourceName));
    	ZipEntry ze = zis.getNextEntry();
 
    	while(ze!=null){
    	   String fileName = ze.getName();
           File newFile = new File(outputFolder + File.separator + fileName);
//           System.out.println("file unzip : "+ newFile.getAbsoluteFile());
           if (ze.isDirectory()){
                String temp = newFile.getAbsolutePath();
                new File(temp).mkdirs();
           }
           else{
               String directory = newFile.getParent();
               if(directory!=null){
                   File d = new File (directory);
                   if(!d.exists()){
                       d.mkdirs();
                   }
               }
               FileOutputStream fos = new FileOutputStream(newFile);
               int len; while ((len = zis.read(buffer)) > 0) {
               fos.write(buffer, 0, len); }
               fos.close();
           }  
            ze = zis.getNextEntry();
    	}
 
        zis.closeEntry();
    	zis.close();
 
    }catch(IOException ex){
        System.err.println("Error while extracting the reosurces: "+ex.getMessage());
    }
   } 
    
    public static void copy(InputStream is, File dest)throws Exception{
        OutputStream os = null;
        try {
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
        catch(Exception e){
            System.err.println("Exception while copying resource. "+e.getMessage());
            throw e;
        }
        finally {
            if(is!=null)is.close();
            if(os!=null)os.close();
        }
    }

}
