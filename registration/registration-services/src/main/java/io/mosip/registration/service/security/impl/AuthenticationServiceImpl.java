package io.mosip.registration.service.security.impl;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.registration.constants.LoginMode;
import io.mosip.registration.dto.LoginUserDTO;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.util.healthcheck.RegistrationAppHealthCheckUtil;
import io.mosip.registration.util.restclient.AuthTokenUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.mosip.kernel.biometrics.constant.BiometricFunction;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biosdk.provider.factory.BioAPIFactory;
import io.mosip.kernel.biosdk.provider.spi.iBioProviderApi;
import io.mosip.kernel.core.bioapi.exception.BiometricException;
import io.mosip.kernel.core.cbeffutil.entity.BDBInfo;
import io.mosip.kernel.core.cbeffutil.entity.BIR;
import io.mosip.kernel.core.cbeffutil.entity.BIR.BIRBuilder;
import io.mosip.kernel.core.cbeffutil.jaxbclasses.PurposeType;
import io.mosip.kernel.core.cbeffutil.jaxbclasses.RegistryIDType;
import io.mosip.kernel.core.cbeffutil.jaxbclasses.SingleType;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.dao.UserDetailDAO;
import io.mosip.registration.dto.AuthTokenDTO;
import io.mosip.registration.dto.AuthenticationValidatorDTO;
import io.mosip.registration.dto.UserDTO;
import io.mosip.registration.dto.packetmanager.BiometricsDto;
import io.mosip.registration.entity.UserBiometric;
import io.mosip.registration.service.bio.BioService;
import io.mosip.registration.service.login.LoginService;
import io.mosip.registration.service.security.AuthenticationService;
import io.mosip.registration.validator.OTPValidatorImpl;

/**
 * Service class for Authentication
 * 
 * @author SaravanaKumar G
 *
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
	/**
	 * Instance of LOGGER
	 */
	private static final Logger LOGGER = AppConfig.getLogger(AuthenticationServiceImpl.class);

	@Autowired
	private LoginService loginService;

	@Autowired
	private OTPValidatorImpl otpValidatorImpl;

	@Autowired
	private BioAPIFactory bioAPIFactory;

	@Autowired
	private UserDetailDAO userDetailDAO;

	@Autowired
	private BioService bioService;

	@Autowired
	private AuthTokenUtilService authTokenUtilService;


	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.service.security.AuthenticationServiceImpl#
	 * authValidator(java.lang.String,
	 * io.mosip.registration.dto.AuthenticationValidatorDTO)
	 */
	public Boolean authValidator(String userId, String modality, List<BiometricsDto> biometrics) {
		LOGGER.info("OPERATOR_AUTHENTICATION", APPLICATION_NAME, APPLICATION_ID,
				modality + " >> authValidator invoked.");
		try {
			BiometricType biometricType = BiometricType.fromValue(modality);
			List<BIR> record = new ArrayList<>();
			List<UserBiometric> userBiometrics = userDetailDAO.getUserSpecificBioDetails(userId, biometricType.value());
			if (userBiometrics.isEmpty())
				return false;
			userBiometrics.forEach(userBiometric -> {
//				record.add(buildBir(userBiometric.getBioIsoImage(), biometricType));

				record.add(bioService.buildBir(userBiometric.getUserBiometricId().getBioAttributeCode(),
						userBiometric.getQualityScore(), userBiometric.getBioIsoImage()));
			});

			List<BIR> sample = new ArrayList<>(biometrics.size());
			biometrics.forEach(biometricDto -> {
				sample.add(bioService.buildBir(biometricDto));
			});

			iBioProviderApi bioProvider = bioAPIFactory.getBioProvider(biometricType, BiometricFunction.MATCH);
			if (Objects.nonNull(bioProvider)) {
				LOGGER.info("OPERATOR_AUTHENTICATION", APPLICATION_NAME, APPLICATION_ID,
						modality + " >> Bioprovider instance found : " + bioProvider);
				return bioProvider.verify(sample, record, biometricType, null);
			}
		} catch (BiometricException | RuntimeException e) {
			LOGGER.error("REGISTRATION - OPERATOR_AUTHENTICATION", APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		}
		return false;
	}

	

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.service.security.AuthenticationServiceImpl#
	 * authValidator(java.lang.String, java.lang.String, java.lang.String)
	 */
	public AuthTokenDTO authValidator(String validatorType, String userId, String otp, boolean haveToSaveAuthToken) {
		return otpValidatorImpl.validate(userId, otp, haveToSaveAuthToken);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.service.security.AuthenticationServiceImpl#
	 * setAuthenticationBaseValidator(java.util.List)
	 */
	/*
	 * @Override
	 * 
	 * @Autowired public void
	 * setAuthenticationBaseValidator(List<AuthenticationBaseValidator>
	 * authBaseValidators) { this.authenticationBaseValidators = authBaseValidators;
	 * }
	 */

	/**
	 * to validate the password and send appropriate message to display.
	 *
	 * @param authenticationValidatorDTO - DTO which contains the username and
	 *                                   password entered by the user
	 * @return appropriate message after validation
	 */
	public String validatePassword(AuthenticationValidatorDTO authenticationValidatorDTO) {
		LOGGER.debug("REGISTRATION - OPERATOR_AUTHENTICATION", APPLICATION_NAME, APPLICATION_ID,
				"Validating credentials using database >>>> " + authenticationValidatorDTO.getUserId());
		try {
			if(RegistrationAppHealthCheckUtil.isNetworkAvailable()) {
				LoginUserDTO loginUserDTO = new LoginUserDTO();
				loginUserDTO.setUserId(authenticationValidatorDTO.getUserId());
				loginUserDTO.setPassword(authenticationValidatorDTO.getPassword());
				authTokenUtilService.getAuthTokenAndRefreshToken(LoginMode.PASSWORD, loginUserDTO);
			}

			UserDTO userDTO = loginService.getUserDetail(authenticationValidatorDTO.getUserId());

			if (null != userDTO && null != userDTO.getSalt() && HMACUtils2
							.digestAsPlainTextWithSalt(authenticationValidatorDTO.getPassword().getBytes(),
									CryptoUtil.decodeBase64(userDTO.getSalt()))
							.equals(userDTO.getUserPassword().getPwd())) {
				return RegistrationConstants.PWD_MATCH;
			} else {
				return RegistrationConstants.PWD_MISMATCH;
			}

		} catch (RuntimeException | RegBaseCheckedException | NoSuchAlgorithmException runtimeException) {
			LOGGER.info("REGISTRATION - OPERATOR_AUTHENTICATION", APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(runtimeException));
			return RegistrationConstants.PWD_MISMATCH;
		}
	}

	/*
	 * @Override public Boolean validateBiometrics(String validatorType,
	 * List<BiometricsDto> listOfBiometrics) { for (AuthenticationBaseValidator
	 * validator : authenticationBaseValidators) { if
	 * (validator.getClass().getName().toLowerCase().contains(validatorType.
	 * toLowerCase())) { return validator.bioMerticsValidator(listOfBiometrics); } }
	 * 
	 * return false; }
	 */

}
