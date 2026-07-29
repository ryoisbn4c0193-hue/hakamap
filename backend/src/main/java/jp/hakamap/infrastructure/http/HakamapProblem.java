package jp.hakamap.infrastructure.http;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class HakamapProblem {
  private HakamapProblem() {}

  public static ProblemDetail create(HttpStatus status, String code) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create("urn:hakamap:problem:" + code));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    return problem;
  }
}
