package jp.hakamap.project.domain.service;

import java.util.UUID;

@FunctionalInterface
public interface UuidSource {
  UUID next();
}
