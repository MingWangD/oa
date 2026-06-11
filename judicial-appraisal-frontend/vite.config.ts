import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080';

  return {
    plugins: [
      vue(),
      Components({
        dts: false,
        resolvers: [
          ElementPlusResolver({
            importStyle: 'css'
          })
        ]
      })
    ],
    build: {
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined;
            }
            const normalizedId = id.replace(/\\/g, '/');
            if (
              normalizedId.includes('/node_modules/vue/') ||
              normalizedId.includes('/node_modules/@vue/') ||
              normalizedId.includes('/node_modules/pinia/') ||
              normalizedId.includes('/node_modules/vue-router/')
            ) {
              return 'vue-vendor';
            }
            if (
              normalizedId.includes('/node_modules/@vueuse/') ||
              normalizedId.includes('/node_modules/@floating-ui/') ||
              normalizedId.includes('/node_modules/async-validator/') ||
              normalizedId.includes('/node_modules/dayjs/') ||
              normalizedId.includes('/node_modules/lodash-es/') ||
              normalizedId.includes('/node_modules/lodash-unified/') ||
              normalizedId.includes('/node_modules/memoize-one/') ||
              normalizedId.includes('/node_modules/normalize-wheel-es/')
            ) {
              return 'ui-utils';
            }
            return 'vendor';
          }
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        }
      }
    }
  };
});
