FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && mkdir /app/data && chown node:node /app/data
COPY server ./server
COPY web ./web
USER node
ENV PORT=8787 DATA_DIR=/app/data NODE_ENV=production
EXPOSE 8787
VOLUME ["/app/data"]
CMD ["node", "server/main.mjs"]
