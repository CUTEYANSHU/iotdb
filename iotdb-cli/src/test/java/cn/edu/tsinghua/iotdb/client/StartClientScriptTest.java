package cn.edu.tsinghua.iotdb.client;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StartClientScriptTest {


	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws IOException, InterruptedException {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.startsWith("windows")) {
			testStartClientOnWindows();
		} else {
			testStartClientOnUnix();
		}
	}

	private void testStartClientOnWindows() throws IOException {
		final String[] output = { 
				"````````````````````````", 
				"Starting IoTDB Client", "````````````````````````",
				"IoTDB> Connection Error, please check whether the network is avaliable or the server has started.. Host is 127.0.0.1, port is 6668." 
			};
		String dir = getCurrentPath("cmd.exe", "/c", "echo %cd%");
		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c",
				dir + File.separator + "cli" + File.separator + "bin" + File.separator + "start-client.bat", "-h",
				"127.0.0.1", "-p", "6668", "-u", "root", "-pw", "root");
		testOutput(builder, output);
	}
	
	private void testStartClientOnUnix() throws IOException {
		final String[] output = { 
				"---------------------", 
				"Starting IoTDB Client", 
				"---------------------",
				"IoTDB> Connection Error, please check whether the network is avaliable or the server has started.. Host is 127.0.0.1, port is 6668." 
			};
		String dir = getCurrentPath("pwd");
		System.out.println(dir);
		ProcessBuilder builder = new ProcessBuilder("sh",
				dir + File.separator + "cli" + File.separator + "bin" + File.separator + "start-client.sh", "-h",
				"127.0.0.1", "-p", "6668", "-u", "root", "-pw", "root");
		testOutput(builder, output);
	}
	
	private void testOutput(ProcessBuilder builder, String[] output) throws IOException {
		builder.redirectErrorStream(true);
		Process p = builder.start();
		BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		int i = 0;
		while (true) {
			line = r.readLine();
			if (line == null) {
				break;
			}
			assertEquals(output[i], line);
			i++;
		}
		r.close();
		p.destroy();
	}

	private String getCurrentPath(String...command) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process p = builder.start();
		BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String path = r.readLine();
		return path;
	}
}
