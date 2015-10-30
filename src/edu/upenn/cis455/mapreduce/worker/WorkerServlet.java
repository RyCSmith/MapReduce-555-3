package edu.upenn.cis455.mapreduce.worker;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.*;
import javax.servlet.http.*;

import edu.upenn.cis455.mapreduce.Job;

public class WorkerServlet extends HttpServlet {
	public static enum Status { MAPPING, WAITING, REDUCING, IDLE };
	StatusUpdateThread update;
	static final long serialVersionUID = 455555002;
	String port;
	Status status;
	String jobClass;
	Integer keysRead;
	Integer keysWritten;
	String storageRoot;
	
	/**
	 * Servlet is initialized on container startup and doeds not wait for first request.
	 * Starts a status updates thread.
	 */
	@Override
	public void init(ServletConfig config) {
		port = config.getInitParameter("port");
		status = Status.IDLE;
		jobClass = null;
		keysRead = 0;
		keysWritten = 0;
		update = new StatusUpdateThread(this, config.getInitParameter("master"));
		storageRoot = config.getInitParameter("storagedir");
		update.start();
		makeDirectories();
	}
	
	private void makeDirectories() {
		String spoolInPath;
		String spoolOutPath;
		if (storageRoot.endsWith("/")) {
			spoolInPath = storageRoot + "spool-in";
			spoolOutPath = storageRoot + "spool-out";
		}
		else {
			spoolInPath = storageRoot + "/spool-in";
			spoolOutPath = storageRoot + "/spool-out";
		}
		//empties and deletes directories if they exist, creates new ones
		File spoolInFile = new File(spoolInPath);
		File spoolOutFile = new File(spoolOutPath);
		if (spoolInFile.exists()) {
			File[] files = spoolInFile.listFiles();
			for (File file : files) {
				file.delete();
			}
		    spoolInFile.delete();
		}
		if (spoolOutFile.exists()) {
			File files[] = spoolOutFile.listFiles();
			for (File file : files) {
				file.delete();
			}
		    spoolOutFile.delete();
		}
		spoolInFile.mkdir();
		spoolOutFile.mkdir();		
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><title>Worker</title></head>");
		out.println("<body>Hi, I am the worker!</body></html>");
	}
	
	/**
	 * Default handler for POST requests. Distributes the request to the appropriate
	 * method based on path.
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
		if (request.getServletPath().equalsIgnoreCase("/runmap"))
			mapHandler(request, response);
		else if (request.getServletPath().equalsIgnoreCase("/runreduce"))
			reduceHandler(request, response);
	}
	
	/**
	 * Handles POST requests for mapping operations.
	 * @param request
	 * @param response
	 * @throws java.io.IOException
	 */
	public void mapHandler(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
		status = Status.MAPPING;
		String jobClass = request.getParameter("job");
		int numThreads = Integer.parseInt(request.getParameter("job"));
		String relativeInputDir = request.getParameter("input");
		ArrayList<ArrayList<FileAssignment>> fileAssignments = splitWork(numThreads, getFullDirectoryPath(relativeInputDir));
		//load class
		Class servletClass;
		try {
			servletClass = Class.forName(jobClass);
			Job servlet = (Job) servletClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		}
		
		
		status = Status.WAITING;
	}
	
	public ArrayList<ArrayList<FileAssignment>> splitWork(int numThreads, String inputDir) {
		//initialize Assignments list
		ArrayList<ArrayList<FileAssignment>> fileAssignments = new ArrayList<ArrayList<FileAssignment>>();
		for (int i = 0; i < numThreads; i++) {
			fileAssignments.add(new ArrayList<FileAssignment>());
		}
		assert (fileAssignments.size() == numThreads);
		
		//check what's in the directory
		File directory = new File(inputDir);
		List<File> files = Arrays.asList(directory.listFiles());
		
		//handle case = more files than threads
		if (files.size() >= numThreads) {
			Collections.sort(files, new FileLengthComparator());
		}
		
		int currentThread = 0;
		boolean countDown = false;
		while (files.size() > numThreads) {
			File current = files.get(0);
			FileAssignment fAssign = new FileAssignment(current);
			fileAssignments.get(currentThread).add(fAssign);
			
			if (countDown) {
				if (currentThread == 0) {
					countDown = false;
					currentThread++;
				} else
					currentThread--;
			} else {
				if (currentThread == fileAssignments.size() - 1) {
					countDown = true;
					currentThread--;
				} else
					currentThread++;
			}
		}
		return fileAssignments;
	}
	
	public String getFullDirectoryPath(String relativePath) {
		String fullPath;
		if (storageRoot.endsWith("/")) {
			if (relativePath.startsWith("/"))
				fullPath = storageRoot + relativePath.substring(1);
			else
				fullPath = storageRoot + relativePath;
		}
		else {
			if (relativePath.startsWith("/"))
				fullPath = storageRoot + relativePath;
			else
				fullPath = storageRoot + "/" + relativePath;
		}
		return fullPath;
	}
	
	/**
	 * Handles POST requests for reducing operations.
	 * @param request
	 * @param response
	 * @throws java.io.IOException
	 */
	public void reduceHandler(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
		System.out.println("GOT A REDUCE CALL");
		status = Status.REDUCING;
		
		//do all reduce stuff, update counts (may need to be zeroed out at start) when complete status changes to IDLE
		
		status = Status.IDLE;
	}
	
	
}
  
