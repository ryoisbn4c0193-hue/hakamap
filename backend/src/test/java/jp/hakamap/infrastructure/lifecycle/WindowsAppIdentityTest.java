package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class WindowsAppIdentityTest {
  @Test
  void writesRealWindowIdentityAndRecognizesItsOwningProcess() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    AtomicReference<Frame> reference = new AtomicReference<>();
    EventQueue.invokeAndWait(
        () -> {
          Frame frame = new Frame("Hakamap 検査用");
          frame.setSize(320, 160);
          frame.setVisible(true);
          reference.set(frame);
        });
    Frame frame = reference.get();
    try {
      WindowsAppIdentity windows = new WindowsAppIdentity();
      long window = Pointer.nativeValue(Native.getComponentPointer(frame));
      windows.identify(
          window, Path.of("C:\\Hakamap\\Hakamap.exe"), Path.of("C:\\Hakamap\\app\\hakamap.ico"));
      assertThat(windows.isWindow(window)).isTrue();
      assertThat(windows.ownedProcesses()).contains(ProcessHandle.current().pid());
    } finally {
      EventQueue.invokeAndWait(frame::dispose);
    }
  }
}
