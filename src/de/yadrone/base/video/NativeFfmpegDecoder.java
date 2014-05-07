/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.yadrone.base.video;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author positron
 */
public class NativeFfmpegDecoder implements VideoDecoder {

	private ImageListener listener;
	private boolean doStop = false;
	private Process ffmpegProc;
	//private final ImageInputStream iin;
	private Thread ithread;
	private int vW=-1, vH;

	public NativeFfmpegDecoder() {
		/*ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", "-", "-f", "sdl",
		 "-probesize", "2048", "-flags", "low_delay", "-f",
		 "rawvideo", "-pix_fmt", "rgb24", "-");*/
		ProcessBuilder pb = new ProcessBuilder("ffmpeg", 
				//"-loglevel", "fatal",
				"-probesize", "2048", "-flags", "low_delay",
				"-i", "-",
				//"-fflags", "nobuffer",
				//* use the following to skip half the frames
				//"-filter:v", "setpts=0.5*PTS",
				"-f", "image2pipe",
				//"-c:v", "bmp",
				"-vcodec", "rawvideo","-pix_fmt", "bgr24",
				"-"
				);
		//pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		try {
			ffmpegProc = pb.start();
			//final ImageInputStream iin = javax.imageio.ImageIO.createImageInputStream(ffmpegProc.getInputStream());
			Thread errThread = new StderrThread();
			errThread.setDaemon(true);
			errThread.start();

			ithread = new VideoStreamProcessorThread();
			ithread.setDaemon(true);
			ithread.start();
		} catch(IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void decode(InputStream is) {
		final int BUF_SIZE = 2048;
		byte buf[] = new byte[BUF_SIZE];
		OutputStream out = ffmpegProc.getOutputStream();

		while(!doStop) {
			try {
				int rd = is.read(buf);
				//System.out.println("got "+rd+" bytes from drone");
				if(rd==-1) {
					System.out.println("EOF of video stream, quit");
					break;
				}
				out.write(buf, 0, rd);


				/*if(ffmpegout.available() != 0) {
					rd = ffmpegout.read(buf);
					System.out.println("got "+rd+" bytes from ffmpeg");
					if(rd==-1) {
						System.out.println("EOF of ffmpeg stream, quit");
						break;
					}
					dump.write(buf, 0, rd);
					dump.flush();
				}*/
			} catch(SocketException ex) {
				ex.printStackTrace();
				Thread.currentThread().interrupt();
				break;
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}
		}
	}

	@Override
	public void stop() {
		doStop = true;
	}

	@Override
	public void setImageListener(ImageListener listener) {
		this.listener = listener;
	}

	private class StderrThread extends Thread {
		public StderrThread() {
			super("FFMPEG stderr processor");
		}

		@Override
		public void run() {
			Pattern reg = Pattern.compile("Stream.*Video.*([0-9]{3,})x([0-9]{3,})");
			BufferedReader rd = new BufferedReader(new InputStreamReader(ffmpegProc.getErrorStream()));
			try {
				while(true) {
					String s = rd.readLine();
					if(s==null) {
						System.out.println("FFMPEG stderr EOF");
						break;
					}
					//System.err.println("FFMPEG: "+s);
					if(vW==-1) {
						Matcher m = reg.matcher(s);
						if(m.find()) {
							vW = Integer.parseInt(m.group(1) );
							vH = Integer.parseInt(m.group(2));
							synchronized (NativeFfmpegDecoder.this) { NativeFfmpegDecoder.this.notifyAll(); }
						}
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
			}

		}
	}

	private class VideoStreamProcessorThread extends Thread {
		private final InputStream iin;

		public VideoStreamProcessorThread() {
			super("FFMPEG stdout processor");
			this.iin = ffmpegProc.getInputStream();
		}

		@Override
		public void run() {
			if(vW == -1) {
				try {
					synchronized (NativeFfmpegDecoder.this) {
						NativeFfmpegDecoder.this.wait();
					}
				} catch(InterruptedException e) {
					return;
				}
			}
			BufferedImage ii = new BufferedImage(vW, vH, BufferedImage.TYPE_3BYTE_BGR);
			byte[] bytes = ((DataBufferByte) ii.getRaster().getDataBuffer()).getData();
			int len = bytes.length;
			int pos=0, rd;
			while(!isInterrupted()) {
				try {
					//BufferedImage ii = javax.imageio.ImageIO.read(iin);
					rd = iin.read(bytes, pos, len-pos);
					if(rd==-1) {
						System.out.println("FFMPEG stdout EOF");
						break;
					}
					pos += rd;
					if(pos==len) {
						pos=0;
						if(listener!=null) listener.imageUpdated(ii);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					break;
				}
			}
			doStop = true;
		}
	}

}
