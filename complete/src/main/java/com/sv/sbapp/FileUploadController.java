package com.sv.sbapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.sv.sbapp.storage.StorageFileNotFoundException;
import com.sv.sbapp.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@Controller
public class FileUploadController implements ErrorController{
	
	    public static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

		private static final String APPLICATION_NAME = "Drive API Quickstart";
		 
		private static final java.io.File DATA_STORE_DIR = new java.io.File(
				 System.getProperty("user.home"), ".credentials/drive-java-quickstart");
		private static  FileDataStoreFactory  DATA_STORE_FACTORY;
					 
		private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
		private static final String PATH = "/error";
		
		private static HttpTransport  HTTP_TRANSPORT;
				
		private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);
		private static final String CREDENTIALS_FILE_PATH = "/client_secret.json";

	    static {
	    	try {
	    		HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
	    		DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
	    		
	    	}catch(Throwable t) {
	    		t.printStackTrace();
	    		System.exit(1);
	    	}
	    }

    private final StorageService storageService;

    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }
    
    

    @GetMapping("/")
    public String listUploadedFiles(Model model) throws Exception {
    	 	 
         Drive service = getDriveService();
              
         // Print the names and IDs for up to 20 files.
         FileList result = service.files().list()
        		 .setQ("mimeType != 'application/vnd.google-apps.folder' and trashed = false")   
                 .setSpaces("drive")
                // .setPageSize(20)
                 .setFields("nextPageToken, files(id, name, parents, webViewLink, webContentLink)")
                 .execute();
         
         FileList resultFolders = service.files().list()
        		 .setQ("mimeType = 'application/vnd.google-apps.folder' and trashed = false")   
                 .setSpaces("drive")
                // .setPageSize(20)
                 .setFields("nextPageToken, files(id, name, mimeType)")
                 .execute();
         
         List<File> files = result.getFiles();
         List<File> folders = resultFolders.getFiles();
         
         log.info("folders:::"+folders);
         log.info("files:::"+files);
         
         model.addAttribute("files",files);
         model.addAttribute("folders",folders);
        
       /* if (folders == null || folders.isEmpty()) {
        	log.info("No folders found.");
         } else {
             for (File file : folders) {
            	 log.info("%s %s\n", file.getName(), file.getId());
             }
         }
        if (files == null || files.isEmpty()) {
        	log.info("No folders found.");
         } else {
             for (File file : files) {
            	 log.info("%s %s %s\n", file.getName(), file.getId(), file.getMimeType());
             }
         }*/
         

        /*model.addAttribute("files", storageService
                .loadAll()
                .map(path ->
                        MvcUriComponentsBuilder
                                .fromMethodName(FileUploadController.class, "serveFile", path.getFileName().toString())
                                .build().toString())
                .collect(Collectors.toList()));*/

        return "uploadForm";
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws Exception {
    	
    	 
/*         Drive service = getDriveService();
    	
    	
    	OutputStream outputStream = new ByteArrayOutputStream();
    	service.files().get(filename)
    	    .executeMediaAndDownloadTo(outputStream);*/

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+file.getFilename()+"\"")
                .body(file);
    }
    
    @PostMapping("/createFolder")
    public String createFolder(@RequestParam("foldername") String folderName, @RequestParam("selfoldername") String selfolderName, 
    		RedirectAttributes redirectAttributes) throws Exception {
    	
    	log.info("sel Folder ID: " + selfolderName);
        
        String setFields = "id";
        
        if(!selfolderName.equals("default"))
        	setFields = "id,parents";
              	 

    	Drive service = getDriveService();
    	File fileMetadata = new File();
    	fileMetadata.setName(folderName);
    	if(!selfolderName.equals("default"))
    		fileMetadata.setParents(Collections.singletonList(selfolderName));

    	fileMetadata.setMimeType("application/vnd.google-apps.folder");

    	File file = service.files().create(fileMetadata)
    	   
    	  .setFields(setFields)
    	    .execute();

    	redirectAttributes.addFlashAttribute("message","You successfully created folder : " + folderName);        
    	return "redirect:/";
    	
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,@RequestParam("selfoldername") String selfolderName,
                                   RedirectAttributes redirectAttributes) throws Exception {

    	log.info("sel Folder ID: " + selfolderName);
        storageService.store(file);
        String setFields = "id";
        
        if(!selfolderName.equals("default"))
        	setFields = "id,parents";
              	 
        Drive service = getDriveService();
        //String folderId = "1aNL6ULRRwO03m5uC4Ef3TTX580EI1G82";

        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        if(!selfolderName.equals("default"))
        	fileMetadata.setParents(Collections.singletonList(selfolderName));

        java.io.File filePath = new java.io.File("upload-dir/"+file.getOriginalFilename());        
        FileContent mediaContent = new FileContent(file.getContentType(), filePath);        
        File f = service.files().create(fileMetadata, mediaContent)
        
            .setFields(setFields)
                
            .execute();
                
        redirectAttributes.addFlashAttribute("message","You successfully uploaded : " + file.getOriginalFilename());        
        redirectAttributes.addFlashAttribute("linktai","https://drive.google.com/open?id="+f.getId());

        return "redirect:/";
    }

  
	@ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
    
    public static Credential authorize() throws Exception {
        // Load client secrets.
        InputStream in = FileUploadController.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        //log.info("credentials saved to"+DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }
    
    public static Drive getDriveService() throws Exception {
    	
    	 final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
      	 
         Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, authorize())
                 .setApplicationName(APPLICATION_NAME)
                 .build();
		return service;
    }



	@Override
	public String getErrorPath() {
		// TODO Auto-generated method stub
		return null;
	}
    
    
   /* @RequestMapping(value=PATH,method=RequestMethod.GET)
    public String defaultErrorMsg()
    {
    	return "no resource found";
    }

	@Override
	public String getErrorPath() {
		// TODO Auto-generated method stub
		return PATH;
	}*/

}
