package io.mosip.registration.util.scan;

import static io.mosip.registration.constants.LoggerConstants.LOG_REG_DOC_SCAN_CONTROLLER;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.sarxos.webcam.Webcam;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.device.scanner.IMosipDocumentScannerService;
import io.mosip.registration.device.scanner.dto.ScanDevice;
import io.mosip.registration.device.webcam.impl.WebcamSarxosServiceImpl;
import io.mosip.registration.dto.packetmanager.BiometricsDto;

/**
 * This class is used to select the document scanner provider and scan the
 * documents with the provider. The scanned documents are in the byte array
 * which will be converted into bufffered images. The buffered images are then
 * converted to a single image/pdf based on the user option.
 * 
 * @author balamurugan ramamoorthy
 * @since 1.0.0
 */
@Component
public class DocumentScanFacade {

	private IMosipDocumentScannerService documentScannerService;

	private List<IMosipDocumentScannerService> documentScannerServices;

	private static final Logger LOGGER = AppConfig.getLogger(DocumentScanFacade.class);

	@Autowired
	private WebcamSarxosServiceImpl webcamSarxosServiceImpl;

	/**
	 * <p>
	 * This method will get the stubbed image(Image in the local path) as bytes and
	 * return as byte array
	 * </p>
	 * 
	 * @return byte[] - image file in bytes
	 * @throws IOException - holds the ioexception
	 */
	public BufferedImage getScannedDocument() throws IOException {

		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Reading byte array from Scanner");

		InputStream inputStream = this.getClass().getResourceAsStream(RegistrationConstants.DOC_STUB_PATH);

		return ImageIO.read(inputStream);

	}

	/**
	 * <p>
	 * Gets all the document scanner providers in which this class is implemented
	 * </p>
	 * 
	 * @param documentScannerServices - list that holds the scanner impl details
	 */
	@Autowired
	public void setFingerprintProviders(List<IMosipDocumentScannerService> documentScannerServices) {
		this.documentScannerServices = documentScannerServices;
	}

	/**
	 * <p>
	 * Checks the platform and selects the scanner implementation accordingly
	 * </p>
	 * <p>
	 * Currently available Platforms:
	 * </p>
	 * <ol>
	 * <li>Windows</li>
	 * <li>Linux</li>
	 * </ol>
	 * 
	 * @return boolean - sets the scanner factory and returns whether it is set
	 *         properly or not
	 */
	public boolean setScannerFactory() {

		for (IMosipDocumentScannerService documentScannerService : documentScannerServices) {
			LOGGER.info(LOG_REG_DOC_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					"Checking the list of connected devices with the scannerService... "
							+ documentScannerService.getClass().getName());
			try {
				if (!documentScannerService.getDevices().isEmpty()) {
					LOGGER.info(LOG_REG_DOC_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
							RegistrationConstants.APPLICATION_ID,
							"Found the list of connected devices with the scannerService... "
									+ documentScannerService.getClass().getName());

					this.documentScannerService = documentScannerService;
					return true;
				}
				LOGGER.info(LOG_REG_DOC_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						"Unable to find the list of connected devices with the scannerService... "
								+ documentScannerService.getClass().getName());
			} catch (Exception exception) {
				LOGGER.error(LOG_REG_DOC_SCAN_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
						"Exception while loading the documentScannerService "
								+ documentScannerService.getClass().getName() + " - "
								+ ExceptionUtils.getStackTrace(exception));
			}
		}
		return webcamSarxosServiceImpl.getWebCams() != null && !webcamSarxosServiceImpl.getWebCams().isEmpty();
	}

	public boolean setStubScannerFactory() {
		if (!documentScannerServices.isEmpty()) {
			this.documentScannerService = documentScannerServices.get(0);
			return true;
		}
		return false;
	}

	/**
	 * Gets the list of scanner devices that are connected.
	 * 
	 * @return - List of connected devices.
	 */
	public List<ScanDevice> getDevices() {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Getting the list of connected scanner devices");

		List<ScanDevice> scanDevices = null;

		if (documentScannerService != null) {
			scanDevices = documentScannerService.getDevices();

		}

		List<Webcam> webcams = webcamSarxosServiceImpl.getWebCams();

		if (webcams != null && !webcams.isEmpty()) {
			scanDevices = scanDevices == null ? new LinkedList<ScanDevice>() : scanDevices;

			for (Webcam webcam : webcams) {

				ScanDevice scanDevice = new ScanDevice();

				scanDevice.setName(webcam.getName());
				scanDevice.setId(webcam.getName());
				scanDevice.setWebCam(true);

				scanDevices.add(scanDevice);
			}
		}
		return scanDevices;
	}

	/**
	 * scans the document from the scanner
	 * 
	 * @param deviceName - name of selected scanner device
	 * @return BufferedImage- scanned file
	 * @throws IOException - holds the ioexception
	 */
	public BufferedImage getScannedDocumentFromScanner(String deviceName) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Reading byte array from Scanner");

		BufferedImage bufferedImage = null;

		ScanDevice scanDevice = getScanDevice(deviceName);

		if (scanDevice != null) {
			if (scanDevice.isWebCam()) {
				bufferedImage = webcamSarxosServiceImpl.captureImage();
			} else if (scanDevice.isWIA()) {
				bufferedImage = documentScannerService.scan(deviceName);
			}

		}

		return bufferedImage;

	}

	/**
	 * scans the document from the scanner
	 * 
	 * @return BufferedImage- scanned file
	 * @throws IOException - holds the ioexception
	 */
//	public BufferedImage getScannedDocumentFromScanner() throws IOException {
//
//		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
//				RegistrationConstants.APPLICATION_ID, "Reading byte array from Scanner");
//
//		return documentScannerService.scan();
//
//	}

	/**
	 * converts Buffredimage to byte[]
	 * 
	 * @param bufferedImage - scanned file
	 * @return byte[] - holds the image data in bytes
	 * @throws IOException - holds the ioexception
	 */
	public byte[] getImageBytesFromBufferedImage(BufferedImage bufferedImage) throws IOException {

		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Reading byte array from Scanner");

		return documentScannerService.getImageBytesFromBufferedImage(bufferedImage);

	}

	/**
	 * converts all the captured scanned docs to single image file
	 * 
	 * @param bufferedImages - scanned files
	 * @return byte[] - image in bytes
	 * @throws IOException - holds the ioexception
	 */
	public byte[] asImage(List<BufferedImage> bufferedImages) throws IOException {

		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Reading byte array from Scanner");

		return documentScannerService.asImage(bufferedImages);

	}

	/**
	 * converts all the captured scanned docs to single pdf file
	 * 
	 * @param bufferedImages - scanned files
	 * @return byte[] - pdf file in bytes
	 * @throws IOException - holds the ioexception
	 */
	public byte[] asPDF(List<BufferedImage> bufferedImages) throws IOException {

		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Reading byte array from Scanner");

		return documentScannerService.asPDF(bufferedImages);

	}

	/**
	 * converts single pdf file into list of images in order show it in the doc
	 * preview
	 * 
	 * @param pdfBytes - pdf in bytes
	 * @return List - list of image files
	 * @throws IOException - holds the ioexception
	 */
	public List<BufferedImage> pdfToImages(byte[] pdfBytes) throws IOException {

		return documentScannerService.pdfToImages(pdfBytes);
	}

	/**
	 * checks the scanner connectivity
	 * 
	 * @return boolean - true if connected or else false
	 */
//	public boolean isConnected() {
//		return documentScannerService.isConnected();
//	}

	public ScanDevice getScanDevice(String deviceName) {

		List<ScanDevice> scanDevices = getDevices();

		if (scanDevices != null) {
			Optional<ScanDevice> selectedDevice = scanDevices.stream()
					.filter(device -> device.getName().equalsIgnoreCase(deviceName)).findFirst();
			if (selectedDevice.isPresent()) {
				return selectedDevice.get();
			}
		}

		return null;

	}

}
