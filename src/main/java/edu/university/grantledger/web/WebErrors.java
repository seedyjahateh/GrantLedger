package edu.university.grantledger.web;

import edu.university.grantledger.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = WebController.class)
public class WebErrors {
  @ExceptionHandler(DomainException.class)
  ModelAndView domain(DomainException error, HttpServletRequest request) {
    return view(error.status(), error.getMessage(), request);
  }

  @ExceptionHandler(DataAccessException.class)
  ModelAndView database(DataAccessException error, HttpServletRequest request) {
    return view(
        503,
        "The operation could not complete. Return to the form and retry with the same submission key.",
        request);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.validation.BindException.class
  })
  ModelAndView invalid(Exception error, HttpServletRequest request) {
    return view(400, "Check the form values and required fields.", request);
  }

  private ModelAndView view(int status, String detail, HttpServletRequest request) {
    var view = new ModelAndView("problem");
    view.setStatus(org.springframework.http.HttpStatus.valueOf(status));
    view.addObject("status", status);
    view.addObject("detail", detail);
    view.addObject("traceId", ApiController.requestId(request));
    return view;
  }
}
