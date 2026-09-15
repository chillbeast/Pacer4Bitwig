import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './styles/tokens.css';
import './styles/base.css';
import './styles/layout.css';
import './styles/device.css';
import './styles/components.css';
import './styles/panels.css';
import './styles/wave2.css';
import './styles/wave3.css';
import { registerServiceWorker } from './app/pwa';

registerServiceWorker();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
