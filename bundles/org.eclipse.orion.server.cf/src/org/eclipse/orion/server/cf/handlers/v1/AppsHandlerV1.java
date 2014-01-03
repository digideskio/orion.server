/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.handlers.v1;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppsHandlerV1 extends AbstractRESTHandler<App> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public AppsHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected App buildResource(HttpServletRequest request, String path) throws CoreException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected CFJob handleGet(App app, HttpServletRequest request, HttpServletResponse response, final String path) {
		final String contentLocation = IOUtilities.getQueryParameter(request, "contentLocation");
		final String name = IOUtilities.getQueryParameter(request, "name");

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetMap().getTarget(userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (target.getSpace() == null) {
						String msg = "Space not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (contentLocation != null || name != null) {
						return new GetAppCommand(target, name, contentLocation).doIt();
					}
					return getApps(target);
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	@Override
	protected CFJob handlePost(App resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final JSONObject jsonData = extractJSONData(request);

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetMap().getTarget(userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (target.getSpace() == null) {
						String msg = "Space not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					String state = jsonData.optString(CFProtocolConstants.KEY_STATE);
					String name = jsonData.optString(CFProtocolConstants.KEY_NAME);
					String dir = jsonData.optString(CFProtocolConstants.KEY_DIR);

					GetAppCommand getAppCommand = new GetAppCommand(target, name, dir);
					getAppCommand.doIt();
					App app = getAppCommand.getApp();

					if (CFProtocolConstants.KEY_STARTED.equals(state)) {
						return new StartAppCommand(target, app).doIt();
					} else if (CFProtocolConstants.KEY_STOPPED.equals(state)) {
						return new StopAppCommand(target, app).doIt();
					}

					/* push new application */
					if (app == null) {
						String application = jsonData.getString(CFProtocolConstants.KEY_APP);
						String contentLocation = jsonData.getString(CFProtocolConstants.KEY_CONTENT_LOCATION);

						app = new App();
						app.setName(application);
						app.setContentLocation(contentLocation);
					}

					return new PushAppCommand(target, app).doIt();
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	private IStatus getApps(Target target) throws Exception {
		URI appsURI = URIUtil.toURI(target.getUrl());
		String appsUrl = target.getSpace().getJSONObject("entity").getString("apps_url");
		appsUrl = appsUrl.replaceAll("apps", "summary");
		appsURI = appsURI.resolve(appsUrl);

		GetMethod getMethod = new GetMethod(appsURI.toString());
		HttpUtil.configureHttpMethod(getMethod, target);
		CFActivator.getDefault().getHttpClient().executeMethod(getMethod);

		String response = getMethod.getResponseBodyAsString();
		JSONObject result = new JSONObject(response);

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
	}
}
