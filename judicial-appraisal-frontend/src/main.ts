import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { createAppRouter } from './router';
import { useAuthStore } from './stores/auth';
import './style.css';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';

async function bootstrap(): Promise<void> {
  const app = createApp(App);
  const pinia = createPinia();
  const router = createAppRouter(pinia);
  const authStore = useAuthStore(pinia);

  await authStore.restoreSession();

  app.use(pinia);
  app.use(router);
  app.mount('#app');
}

void bootstrap();
