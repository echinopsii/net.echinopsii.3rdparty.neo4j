/**
 * Copyright (c) 2002-2010 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.web;

import org.neo4j.server.configuration.Configurator;
import sun.misc.BASE64Decoder;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.neo4j.server.web.ConfigUtils.NEO_SERVER_AUTH_PASS_KEY;
import static org.neo4j.server.web.ConfigUtils.NEO_SERVER_AUTH_USER_KEY;

/**
 * @author tbaum
 * @since 23.01.11
 */
public class SecurityFilter implements Filter {

    private String name;
    private String pass;

    public void init(FilterConfig config) throws ServletException {
        final Configurator configurator= (Configurator) config.getServletContext().getAttribute(ContextListener.CONFIGURATOR_KEY);

        name = configurator.configuration().getString(NEO_SERVER_AUTH_USER_KEY);
        pass = configurator.configuration().getString(NEO_SERVER_AUTH_PASS_KEY);
    }

    public void doFilter(final ServletRequest req, final ServletResponse res, final FilterChain chain)
            throws ServletException, IOException {
        if (!(req instanceof HttpServletRequest) || !(res instanceof HttpServletResponse)) {
            throw new ServletException("request not allowed");
        }

        final HttpServletRequest request = (HttpServletRequest) req;
        final HttpServletResponse response = (HttpServletResponse) res;

        final String header = request.getHeader("Authorization");

        if (checkAuth(header)) {
            chain.doFilter(request, response);
        } else {
            sendAuthHeader(response);
        }
    }

    public void destroy() {
    }

    private boolean checkAuth(String header) throws IOException {
        if (name == null || pass == null) {
            return true;
        }

        if (header == null) {
            return false;
        }

        final String encoded = header.substring(header.indexOf(" ") + 1);

        final String decoded = new String(new BASE64Decoder().decodeBuffer(encoded));
        final int split = decoded.indexOf(":");
        final String n = decoded.substring(0, split);
        final String p = decoded.substring(split + 1);

        return name.equals(n) && pass.equals(p);
    }

    private void sendAuthHeader(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"neo4j\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
