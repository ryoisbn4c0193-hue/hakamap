import { Application } from 'pixi.js';

/** PixiJSのライフサイクルをReactコンポーネントから隔離します。 */
class PixiMapAdapter {
  private application?: Application;

  public async mount(container: HTMLElement): Promise<void> {
    this.destroy();

    const application = new Application();
    await application.init({
      antialias: true,
      backgroundAlpha: 0,
      resizeTo: container,
    });
    container.appendChild(application.canvas);
    this.application = application;
  }

  public destroy(): void {
    this.application?.destroy(true, {
      children: true,
      context: true,
      style: true,
      texture: true,
      textureSource: true,
    });
    this.application = undefined;
  }
}

export default PixiMapAdapter;
