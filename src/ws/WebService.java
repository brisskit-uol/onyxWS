package ws;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import org.brisskit.onyx.pdo2ss.AugmentedListProcessor ;
import org.brisskit.onyx.pdo2ss.FreshListProcessor ;
import org.brisskit.onyx.pdo2ss.Pdo2Spreadsheet; 

import org.apache.poi.hssf.usermodel.HSSFWorkbook ;
  
/**
*
* <brisskit:licence>
* Copyright University of Leicester, 2012
* This software is made available under the terms & conditions of brisskit
* @author Saj Issa
* @author Jeff Lusted
* This resource represents the WebServices to send pdo files to Onyx.
* This service formats the PDO into an appointments' list in Excel spreadsheet format
* and writes the spreadsheet file into the appropriate 'in' directory for Onyx
* to update its appointment list.
* Currently (17 Sept 2012) there is no CIVI call back to update the activity list.
* </brisskit:licence>
*
*/

  
@Path("/service") 

public class WebService {  
	
	final static Logger logger = LogManager.getLogger(WebService.class);
 
	private static final String CONFIG_PATH =
			"/var/brisskit/onyx/tomcat/webapps/onyxWS/WEB-INF/webservice.properties" ; 
//			"/home/jeff/ws-jee/onyxWS/WebContent/WEB-INF/webservice.properties" ;
	
	private static final String COLLECTION_CENTER_ID_KEY = "uk.org.brisskit.onyx.collectionCenterId" ;
	private static final String ONYX_APPOINTMENTS_DIRECTORY_PATH_KEY = "uk.org.brisskit.onyx.appointments.path" ;
	private static final String PDO_AUDIT_PATH_KEY = "uk.org.brisskit.pdo.audit.path" ;
	private static final String TEMPLATE_SPREADSHEET_PATH_KEY = "uk.org.brisskit.spreadsheet.template.path" ;
	
	private Properties config ;
	
//	@GET
//	@Path("catissue")
//	@Produces(MediaType.TEXT_XML)
//	public String getTodosBrowser() {	
//			return ""; 
//	}
		
    @POST  
    @Path("pdo")  
    @Consumes(MediaType.APPLICATION_XML)  
    @Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})  
    public String postOnlyXML2(@FormParam("incomingXML") String incomingXML,@FormParam("activity_id") String activity_id) {  
        System.out.println("incomingXML :" + incomingXML);      
        String str = incomingXML;
        String status = "";
        String status_var="Failed";
        
        //logger.info("replace + with %2B");
        logger.info("************* A PDO HAS ARRIVED *******************");
        logger.info(" ");
        logger.info("CONTENT OF XML " + incomingXML);
        logger.info(" ");
        logger.info("activity_id : " + activity_id);
        
      //Current Date time in Local time zone
        SimpleDateFormat localDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String date = localDateFormat.format( new Date() ) ;

        if (str.contains("xml="))
        {
            str = str.substring(str.indexOf("xml=")+4);
        }
              
		try {
			//
			// Load my configuration properties...
			InputStream inputStream  = new FileInputStream( CONFIG_PATH ) ;
	        this.config = new Properties();
	        this.config.load(inputStream);
	        
//			InputStream inputStream =  this.getClass().getClassLoader().getResourceAsStream( "onyx-config.properties" ) ;
//			logger.info( "inputStream: " + inputStream ) ;
//			this.config = new Properties(); 
//	        this.config.load(inputStream);   
	       
	        
			//
			// First make an XML file out of the PDO input and write the file to 
			// a local directory. Good as an audit trail...
			
			//String param2AfterDecoding = URLDecoder.decode(str, "UTF-8");
			//System.out.println("param2 after decoding:" + param2AfterDecoding);
			
			String pdoFileName = getPdoAuditPath() + System.getProperty( "file.separator" ) + "pdo" + date +".xml" ;
			logger.info(" ");
	        logger.info("FILE NAME : " + pdoFileName ) ;
	        
			FileWriter fstream = new FileWriter( pdoFileName ) ;
			BufferedWriter out = new BufferedWriter(fstream);
			//out.write(param2AfterDecoding);
			out.write(str);
			out.close();
			
			//
			// Now format a spreadsheet from the file...
			// (stuff hard coded here for the moment)... 
			
			//
			// Is there an existing list (or maybe more than one) present in Onyx?...
			File inDirectory = new File( getOnyxApptsDirectoryPath() ) ;
			File[] files = inDirectory.listFiles(  new FilenameFilter() {
				//
				// Only accept spreadsheet files...
				public boolean accept( File dir, String name ) {
					String[] parts = name.split( "\\." ) ;
					if( parts.length < 2 ) {
						return false ;
					}
					else if( parts[ parts.length-1 ].equals( "xls") ) {
						return true ;
					}
					return false ;
				}
			} ) ;
			
			File pdoFile = new File( pdoFileName ) ;
			HSSFWorkbook updatedOnyxApptList = null ;
			
			// OK, we have a list of the files in the Onyx 'in' directory.
			// If no files, this is a new deployment and we need a new list...
			if( files.length == 0 ) {
				File templateSpreadsheetFile = new File( getTemplateSpreadsheetPath() ) ;
				updatedOnyxApptList = produceNewList( templateSpreadsheetFile, pdoFile ) ;
			}
			//
			// If 1 or more files, we need to augment the latest one and write 
			// the result back as a new/latest spreadsheet file of updated appointments...
			// (Onyx will always accept the latest as an updated list) ...
			else {
				//
				// First sort the files (each file has a time stamp at the end)
				// so we are aiming for the latest file.
				// This will be the last in the sorted array...
				Arrays.sort( files ) ;
				File latestSpreadsheetFile = files[ files.length - 1 ] ;
				updatedOnyxApptList = produceAugmentedList( latestSpreadsheetFile, pdoFile ) ;
			}
			
			//
			// We now have a list to write back into the Onyx appointments 'in' directory.
			// We generate a new name and do the business...
			String spreadsheetFullPathName = getOnyxApptsDirectoryPath() + "/appts-" + date +".xls" ;			
			FileOutputStream fileOut = new FileOutputStream( spreadsheetFullPathName ) ;
			updatedOnyxApptList.write(fileOut);
			fileOut.close();
			//
			// If we get this far, at the moment we assume that Onyx will have no problems
			// processing the updated list. This needs to be improved upon.
			status_var = "Completed";
							
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch ( Pdo2Spreadsheet.P2SException p2sex ) {
			p2sex.printStackTrace() ;
		}
		finally {
			//
			// We update Civi, passing across whether the updateto the appointments list was successful
			// civiCallBack( activity_id, status_var ) ;
		}
        return status_var;  
    }
    
    private String getOnyxApptsDirectoryPath() {
    	return this.config.getProperty( ONYX_APPOINTMENTS_DIRECTORY_PATH_KEY ) ;
    }
    
    private String getPdoAuditPath() {
    	return this.config.getProperty( PDO_AUDIT_PATH_KEY ) ;
    }
    
    private String getTemplateSpreadsheetPath() {
    	return this.config.getProperty( TEMPLATE_SPREADSHEET_PATH_KEY ) ;
    }
    
    private String getCollectionCenterId() {
    	return this.config.getProperty( COLLECTION_CENTER_ID_KEY ) ;
    }
    
    private void civiCallBack( String activity_id, String status_var ) {
    	if(! activity_id.equals("X") ) {
    		try {

    			String option_group_id="";
    			String activity_status_id="";

    			ClientConfig config = new DefaultClientConfig();
    			Client client = Client.create(config);

    			/* get options list */
    			WebResource optionGroupService = client.resource(getOptionGroupBaseURI());
    			String responseoptionGroupService = optionGroupService.get(String.class);

    			logger.info(" ");
    			logger.info("responseoptionGroupService............" + responseoptionGroupService);  


    			option_group_id = jsonGetId(optionGroupService.get(String.class), "option_group");

    			logger.info(" ");
    			logger.info("option_group_id............" + option_group_id); 

    			logger.info(" ");
    			logger.info("status_var............" + status_var); 

    			WebResource optionValueService = client.resource(getOptionValueBaseURI(option_group_id,status_var));
    			String responseoptionValueService = optionValueService.get(String.class);

    			logger.info(" ");
    			logger.info("responseoptionValueService............" + responseoptionValueService); 

    			activity_status_id = jsonGetId(optionValueService.get(String.class), "option_value");

    			logger.info(" ");
    			logger.info("activity_status_id............ " + activity_status_id); 


    			WebResource service = client.resource(getBaseURI(activity_id, activity_status_id));

    			ClientResponse response = service.type(MediaType.TEXT_HTML).post(ClientResponse.class); // added

    			logger.info("3");         		  

    			//String response = service.get(String.class);

    			logger.info(" ");
    			//logger.info("response............"+response);           

    			logger.info(" ");
    			logger.info("CIVI CALLBACK COMPLETE");

    		} catch (UniformInterfaceException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (JSONException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
    
    
    private HSSFWorkbook produceNewList( File templateSpreadsheetFile, File pdoFile ) 
    		throws IOException, Pdo2Spreadsheet.P2SException {
    	FileInputStream templateFileStream = new FileInputStream( templateSpreadsheetFile );
		HSSFWorkbook templateWB = new HSSFWorkbook( templateFileStream );
		templateFileStream.close();   	
    	FreshListProcessor flp = new FreshListProcessor( templateWB, pdoFile, getCollectionCenterId() ) ;
    	HSSFWorkbook freshWorkbook = flp.processList() ;
    	return freshWorkbook ;
    }
    
    private HSSFWorkbook produceAugmentedList( File spreadsheetFile, File pdoFile ) 
    		throws IOException, Pdo2Spreadsheet.P2SException {
    	FileInputStream spreadsheetFileStream = new FileInputStream( spreadsheetFile );
    	HSSFWorkbook currentListWB = new HSSFWorkbook( spreadsheetFileStream );
    	spreadsheetFileStream.close();   	
    	AugmentedListProcessor alp = new AugmentedListProcessor( currentListWB, pdoFile, getCollectionCenterId() ) ;
    	HSSFWorkbook augmentedWorkbook = alp.processList() ;
    	return augmentedWorkbook ;
    }
    
    public static String excutePost(String targetURL, String urlParameters)
    {
      URL url;
      HttpURLConnection connection = null;  
      try {
    	  logger.info("1");
        //Create connection
        url = new URL(targetURL);
        connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");
        //connection.setRequestProperty("Content-Type", 
        //     "application/x-www-form-urlencoded");
    
  			
        //connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
        connection.setRequestProperty("Content-Language", "en-US");  

        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.0; pl; rv:1.9.1.2) Gecko/20090729 Firefox/3.5.2");
        //connection.addRequestProperty("Referer", "http://xxxx");
        //connection.addRequestProperty("Cookie", "...");

   
        connection.getResponseCode();	
        connection.setUseCaches (false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        logger.info("2");
        //Send request
        DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream ());
        wr.writeBytes (urlParameters);
        wr.flush ();
        wr.close ();
        logger.info("3");
        //Get Response	
        InputStream is = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuffer response = new StringBuffer(); 
        while((line = rd.readLine()) != null) {
          response.append(line);
          response.append('\r');
        }
        rd.close();
        logger.info("4");
        return response.toString();

      } catch (Exception e) {
    	  logger.info("5");
        e.printStackTrace();
        return null;
        

      } finally {

        if(connection != null) {
          connection.disconnect(); 
        }
        logger.info("6");
      }
    }
    
    //http://bru2.brisskit.le.ac.uk/
    //http://civicrm/
    	
    private static URI getOptionGroupBaseURI() {
    	logger.info("getOptionGroupBaseURI ");
    	logger.info("http://civicrm/civicrm/civicrm/ajax/rest?json=1&debug=1&version=3&entity=OptionGroup&action=get&name=activity_status");
        return 
        		UriBuilder.fromUri("http://civicrm/civicrm/civicrm/ajax/rest?json=1&debug=1&version=3&entity=OptionGroup&action=get&name=activity_status").build(); 
    }
    
    private static URI getOptionValueBaseURI(String option_group_id, String status) {
    	logger.info("getOptionValueBaseURI ");
        logger.info("option_group_id = "+option_group_id);  
        logger.info("status = "+status); 
    	logger.info("http://civicrm/civicrm/civicrm/ajax/rest?json=1&debug=1&version=3&entity=OptionValue&action=get&option_group_id="+option_group_id+"&name="+status);
    	
        return 
        		UriBuilder.fromUri("http://civicrm/civicrm/civicrm/ajax/rest?json=1&debug=1&version=3&entity=OptionValue&action=get&option_group_id="+option_group_id+"&name="+status).build(); 

    }
    
    private static URI getBaseURI(String activity_id, String status_id) {
    	logger.info("getBaseURI ");
        logger.info("activity_id = "+activity_id);  
        logger.info("status_id = "+status_id); 
        logger.info("http://civicrm/civicrm/civicrm/ajax/rest?json=1&debug=1&entity=Activity&action=update&status_id="+status_id+"&id="+activity_id);
        
        return 
        		UriBuilder.fromUri("http://civicrm/civicrm/civicrm/ajax/rest?json=1&debug=1&entity=Activity&action=update&status_id="+status_id+"&id="+activity_id+"&details=").build(); 

    }


    private static String jsonGetId(String service, String option) throws JSONException
    {
        String id="";

        JSONObject obj1 = new JSONObject(service);
        JSONObject obj2 = new JSONObject(obj1.get("values").toString());
        Iterator itr = obj2.keys();

        if(itr.hasNext())
        {
            JSONObject obj3 = new JSONObject(obj2.get(itr.next().toString()).toString());

            if(option.equalsIgnoreCase("option_group"))
                id = obj3.get("id").toString();
            else
                id = obj3.get("value").toString();
        }

        return id;
    }

  
	}  