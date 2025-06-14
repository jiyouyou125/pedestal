/* Copyright 2025 Nubank NA

 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
 * which can be found in the file epl-v10.html at the root of this distribution.
 *
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 *
 * You must not remove this notice, or any other, from this software.
 */

package io.pedestal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A bridge from the servlet APIs to the Pedestal APIs.
 */
public interface ConnectorBridge {

    /** Delegates servicing the request to Pedestal. */
    void service(HttpServletRequest request, HttpServletResponse response);

    /** Invoked when the servlet is destroyed. */
    void destroy();
}
