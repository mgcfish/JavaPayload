/*
 * Java Payloads.
 * 
 * Copyright (c) 2010, 2011 Michael 'mihi' Schierl
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *   
 * - Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *   
 * - Neither name of the copyright holders nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *   
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND THE CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR THE CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package javapayload.handler.stager;

import java.io.PrintStream;

import javapayload.HandlerModule;
import javapayload.handler.dynstager.DynStagerHandler;
import javapayload.handler.stage.StageHandler;

public abstract class StagerHandler extends HandlerModule {
	
	public StagerHandler(String summary, boolean handlerUsable, boolean stagerUsable, String description) {
		super(StagerHandler.class, handlerUsable, stagerUsable, summary, description);
	}
	
	/**
	 * For {@link DynStagerHandler} only.
	 */
	StagerHandler(Class moduleType, String summary, boolean handlerUsable, boolean targetUsable, String description) {
		super(moduleType, handlerUsable, targetUsable, summary, description);
	}
	
	public boolean isStagerUsableWith(DynStagerHandler[] dynstagers) {
		return isTargetUsable();
	}
	
	private boolean ready;
	protected String[] originalParameters;
	
	public static void main(String[] args) throws Exception {
		boolean stageFound = false;
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equals("--")) {
				stageFound = true;
			}
		}
		if (!stageFound) {
			System.out.println("Usage: java javapayload.handler.stager.StagerHandler <stager> [stageroptions] -- <stage> [stageoptions]");
			return;
		}
		new Loader(args).handle(System.err, null);
	}

	// may have side effects on the parameters!
	protected boolean prepare(String[] parametersToPrepare) throws Exception {
		return false;
	}
	
	protected boolean canHandleExtraArg(Class argType) {
		return argType == null;
	}
	
	public synchronized void notifyReady() {
		ready = true;
		notifyAll();
	}
	
	protected synchronized void waitReady() throws InterruptedException {
		while (!ready)
			wait();
	}

	protected abstract void handle(StageHandler stageHandler, String[] parameters, PrintStream errorStream, Object extraArg, StagerHandler readyHandler) throws Exception;
	protected abstract boolean needHandleBeforeStart();
	protected abstract String getTestArguments();
	
	public String[] getTestArgumentArray() {
		String args = getTestArguments();
		return args == null ? null : new String[] {args};
	}
	
	public static StagerHandler getStagerHandler(String stager) throws Exception {
		try {
			return (StagerHandler) Class.forName("javapayload.handler.stager." + stager).newInstance();
		} catch (ClassNotFoundException ex) {
			String lookupName = stager;
			int pos = lookupName.indexOf('_');
			if (pos != -1) {
				String dshName = lookupName.substring(0, pos);
				String stagerName = lookupName.substring(pos+1);
				pos = dshName.indexOf('$');
				if (pos != -1) {
					dshName = dshName.substring(0, pos);
				}
				DynStagerHandler dsh = (DynStagerHandler) Class.forName("javapayload.handler.dynstager."+dshName).newInstance();
				StagerHandler baseStagerHandler = getStagerHandler(stagerName);
				((DynStagerHandlerHelper)dsh).setStagerHandler(baseStagerHandler);
				return dsh;
			}
			throw ex;
		}
	}

	public static class Loader {
		private final String[] args;
		public final StageHandler stageHandler;
		private final StagerHandler stagerHandler;
		private Thread beforeThread = null;
		
		public Loader(String[] args) throws Exception {
			this.args = args;
			String stager = args[0];
			String stage = null;
			for (int i = 0; i < args.length - 1; i++) {
				if (args[i].equals("--")) {
					stage = args[i + 1];
				}
			}
			if (stage == null) {
				throw new IllegalArgumentException("No stage given");
			}
			stageHandler = (StageHandler) Class.forName("javapayload.handler.stage." + stage).newInstance();
			stagerHandler = getStagerHandler(stager);
		}
		
		public void handle(PrintStream errorStream, Object extraArg) throws Exception {
			if (stagerHandler.prepare(args)) {
				errorStream.print("Stager changed parameters:");
				for (int i = 0; i < args.length; i++) {
					errorStream.print(" "+args[i]);
				}
				errorStream.println();
			}
			handleInternal(errorStream, extraArg, false);
		}
		
		public String[] getArgs() {
			return args;
		}
		
		public boolean canHandleExtraArg(Class argType) {
			return stagerHandler.canHandleExtraArg(argType);
		}
		
		private void handleInternal(PrintStream errorStream, Object extraArg, boolean needReadyInformation) throws Exception {
			stagerHandler.originalParameters = args;
			stagerHandler.handle(stageHandler, args, errorStream, extraArg, needReadyInformation ? stagerHandler : null);
		}
		
		public void handleBefore(final PrintStream errorStream, final Object extraArg) throws Exception {
			stagerHandler.prepare(args);
			if (stagerHandler.needHandleBeforeStart()) {
				beforeThread = new Thread(new Runnable() {
					public void run() {
						try {
							handleInternal(errorStream, extraArg, true);
						} catch (final Exception ex) {
							ex.printStackTrace();
						}
					}
				});
				beforeThread.start();
				stagerHandler.waitReady();
			}
		}
		
		public void handleAfter(PrintStream errorStream, Object extraArg) throws Exception {
			if (beforeThread != null)
				beforeThread.join();
			if (!stagerHandler.needHandleBeforeStart())
				handleInternal(errorStream, extraArg, false);
		}
	}
}
