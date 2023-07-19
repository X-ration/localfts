package com.adam.localfts.webserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@SpringBootApplication
public class LocalFtsWebServer {

	private static final Logger logger = LoggerFactory.getLogger(LocalFtsWebServer.class);

	public static void main(String[] args) throws SocketException {
		SpringApplication.run(LocalFtsWebServer.class, args);
		getServerIp();
	}

	private static void getServerIp() throws SocketException {
		Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
		StringBuilder stringBuilder = new StringBuilder("服务器网络信息" + System.lineSeparator());
		while(networkInterfaces.hasMoreElements()) {
			NetworkInterface networkInterface = networkInterfaces.nextElement();
			Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
			int addressCount = 0;
			while(inetAddresses.hasMoreElements()) {
				if(addressCount++ == 0) {
					stringBuilder.append("Interface [").append(networkInterface.getDisplayName())
							.append("][").append(networkInterface.getName()).append("] Addresses: [");
				}
				InetAddress inetAddress = inetAddresses.nextElement();
				stringBuilder.append(inetAddress.getHostAddress()).append(" ");
			}
			if(addressCount > 0) {
				stringBuilder.deleteCharAt(stringBuilder.length() - 1);
				stringBuilder.append("]").append(System.lineSeparator());
			}
		}
		logger.info(stringBuilder.toString());
	}

}
