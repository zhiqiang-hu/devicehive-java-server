package com.devicehive.websockets;

import com.devicehive.configuration.Messages;
import com.devicehive.exceptions.HiveException;
import com.devicehive.websockets.converters.JsonMessageBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;

@Component
public class WebSocketExecutor {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketExecutor.class);

    @Autowired
    private WebSocketRequestProcessor requestProcessor;

    public JsonObject execute(JsonObject request, WebSocketSession session) {
        JsonObject response;
        try {
            response = requestProcessor.process(request, session).getResponseAsJson();
        } catch (BadCredentialsException ex) {
            logger.error("Unauthorized access", ex);
            response = JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials").build();
        } catch (AccessDeniedException ex) {
            logger.error("Access to action is denied", ex);
            response = JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized").build();
        } catch (HiveException ex) {
            logger.error("Error executing the request", ex);
            response = JsonMessageBuilder.createError(ex).build();
        } catch (ConstraintViolationException ex) {
            logger.error("Error executing the request", ex);
            response =
                    JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage())
                            .build();
        } catch (org.hibernate.exception.ConstraintViolationException ex) {
            logger.error("Error executing the request", ex);
            response = JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_CONFLICT, ex.getMessage())
                    .build();
        } catch (JsonParseException ex) {
            logger.error("Error executing the request", ex);
            response = JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_BAD_REQUEST,
                    Messages.INVALID_REQUEST_PARAMETERS).build();
        } catch (OptimisticLockException ex) {
            logger.error("Error executing the request", ex);
            logger.error("Data conflict", ex);
            response = JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_CONFLICT,
                    Messages.CONFLICT_MESSAGE).build();
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                response =
                        JsonMessageBuilder.createErrorResponseBuilder(HttpServletResponse.SC_CONFLICT, ex.getMessage())
                                .build();
            } else {
                response = JsonMessageBuilder
                        .createErrorResponseBuilder(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage())
                        .build();
            }
        } catch (Exception ex) {
            logger.error("Error executing the request", ex);
            response = JsonMessageBuilder
                    .createErrorResponseBuilder(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage()).build();
        }

        return new JsonMessageBuilder()
                .addAction(request.get(JsonMessageBuilder.ACTION))
                .addRequestId(request.get(JsonMessageBuilder.REQUEST_ID))
                .include(response)
                .build();
    }
}
