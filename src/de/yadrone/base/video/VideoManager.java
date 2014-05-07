/*
 *
  Copyright (c) <2011>, <Shigeo Yoshida>
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
The names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.yadrone.base.video;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import de.yadrone.base.command.CommandManager;
import de.yadrone.base.exception.IExceptionListener;
import de.yadrone.base.exception.VideoException;
import de.yadrone.base.manager.AbstractTCPManager;
import de.yadrone.base.utils.ARDroneUtils;
import java.net.ConnectException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VideoManager extends AbstractTCPManager implements ImageListener 
{

	private static final Logger logger = Logger.getLogger(VideoManager.class.getName());

	private IExceptionListener excListener;
	
	private VideoDecoder decoder;

	private CommandManager manager = null;

	private ArrayList<ImageListener> listener = new ArrayList<ImageListener>();

	public VideoManager(InetAddress inetaddr, CommandManager manager, VideoDecoder decoder, IExceptionListener excListener) 
	{
		super(inetaddr);
		this.manager = manager;
		this.decoder = decoder;
		this.excListener = excListener;
	}

	public void addImageListener(ImageListener listener) {
		this.listener.add(listener);
		if (this.listener.size() == 1)
			decoder.setImageListener(this);
	}
	
	public void removeImageListener(ImageListener listener) {
		this.listener.remove(listener);
		if (this.listener.isEmpty())
			decoder.setImageListener(null);
	}

	/** Called only by decoder to inform all the other listener */
	@Override
	public void imageUpdated(BufferedImage image)
	{
		for (int i=0; i < listener.size(); i++)
		{
			listener.get(i).imageUpdated(image);
		}
	}
	
	@Override
	public boolean connect(int port) throws IOException
	{
		if (decoder == null)
			return false;

		return super.connect(port);
	}

	public void reinitialize()
	{
		logger.info("reinitializing video stream ...");
		close();
		logger.info("previous stream closed.");
		try
		{
			logger.info("VideoManager: create new decoder");
			decoder.stop();
			decoder = (VideoDecoder)decoder.getClass().newInstance();
			decoder.setImageListener(this);
			
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		logger.info("start connecting again ...");
		new Thread(this).start();
	}
	
	@Override
	public void run() {
		if (decoder == null)
			return;
		try
		{
			logger.info("connect ");
			connect(ARDroneUtils.VIDEO_PORT);
			
			logger.info("tickle ");
			ticklePort(ARDroneUtils.VIDEO_PORT);
			
//			manager.setVideoBitrateControl(VideoBitRateMode.DISABLED); // bitrate set to maximum
			
			logger.info("decode ");
			/*InputStream ii = getInputStream();
			byte buf[] = new byte[1024];
			int rd;
			while( (rd = ii.read(buf) ) != -1 ) {
				oo.write(buf, 0, rd);
				oo.flush();
			}*/
			decoder.decode(getInputStream());
		}
		catch(ConnectException ce) {
			logger.log(Level.WARNING, "Connect exception, quitting video thread");
			Thread.currentThread().interrupt();
		}
		catch(Exception exc)
		{
			logger.log(Level.WARNING, "exception in video", exc);
			excListener.exeptionOccurred(new VideoException(exc));
		}
		
		close();
		if(!Thread.currentThread().isInterrupted()) reinitialize();
	}

	@Override
	public void close() {
		if (decoder == null)
			return;

		super.close();
	}

}
