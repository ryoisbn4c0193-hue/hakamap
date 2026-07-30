package jp.hakamap.project.application.transfer;

public interface OperationControl {
  OperationControl NONE =
      new OperationControl() {
        @Override
        public void checkpoint() {}

        @Override
        public void beginCommit() {}
      };

  void checkpoint();

  void beginCommit();
}
