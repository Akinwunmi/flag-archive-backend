FROM postgres:16
COPY init.sql /docker-entrypoint-initdb.d/

# Environment variables
ENV POSTGRES_DB flags-db
ENV POSTGRES_USER flags-user
ENV POSTGRES_PASSWORD test