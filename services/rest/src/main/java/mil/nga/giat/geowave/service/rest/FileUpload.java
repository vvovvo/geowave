package mil.nga.giat.geowave.service.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.io.FileUtils;
import org.reflections.Reflections;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Post;
import org.restlet.security.Verifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.restlet.security.SecretVerifier;
import org.restlet.security.Authenticator;
import org.restlet.ext.fileupload.RestletFileUpload;

import mil.nga.giat.geowave.core.cli.annotations.GeowaveOperation;
import mil.nga.giat.geowave.core.cli.operations.config.options.ConfigOptions;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.resource.ResourceException;
import org.restlet.routing.Router;
import org.restlet.data.Status;
import org.restlet.Restlet;

import org.restlet.Restlet;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.data.ChallengeScheme;
import org.restlet.security.MapVerifier;
import org.restlet.Context;

/**
 * ServerResource to handle uploading files. Uses restlet fileupload.
 */
@GeowaveOperation(name = "fileupload", restEnabled = GeowaveOperation.RestEnabledType.POST)
public class FileUpload extends
		ServerResource
{
	private static class UploadedFile
	{
		private final String name;

		UploadedFile(
				String name ) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * processes uploaded file, storing in a temporary directory
	 * 
	 * @param entity
	 * @return the directory storing the uploaded file
	 * @throws Exception
	 */
	@Post
	public UploadedFile accept(
			Representation entity )
			throws Exception {
		UploadedFile result;
		if (entity != null && MediaType.MULTIPART_FORM_DATA.equals(
				entity.getMediaType(),
				true)) {
			// 1/ Create a factory for disk-based file items
			DiskFileItemFactory factory = new DiskFileItemFactory();
			factory.setSizeThreshold(1000240);

			// 2/ Create a new file upload handler based on the Restlet
			// FileUpload extension that will parse Restlet requests and
			// generates FileItems.
			RestletFileUpload upload = new RestletFileUpload(
					factory);

			// 3/ Request is parsed by the handler which generates a
			// list of FileItems
			String tempDir = System.getProperty("java.io.tmpdir");
			File dir = new File(
					tempDir);
			File filename = File.createTempFile(
					"uploadedfile",
					".tmp",
					dir);
			result = new UploadedFile(
					filename.getAbsolutePath());
			List<FileItem> fileList = upload.parseRepresentation(entity);
			if (fileList.size() != 1) {
				throw new ResourceException(
						Status.CLIENT_ERROR_BAD_REQUEST);
			}
			FileUtils.copyInputStreamToFile(
					fileList.get(
							0).getInputStream(),
					filename);
		}
		else {
			throw new ResourceException(
					Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
		}

		return result;
	}

}
