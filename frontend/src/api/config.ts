export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  TIMEOUT: 30000,
  SSE_TIMEOUT: 60000,
  RETRY_COUNT: 3,
  RETRY_DELAY: 1000,
}

export const AUTH_CONFIG = {
  TOKEN_KEY: 'imkqas_token',
  REFRESH_TOKEN_KEY: 'imkqas_refresh_token',
  TOKEN_PREFIX: 'Bearer ',
  TOKEN_REFRESH_THRESHOLD: 300000, // 5分钟前刷新
}

export const HTTP_STATUS = {
  OK: 200,
  CREATED: 201,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_SERVER_ERROR: 500,
}