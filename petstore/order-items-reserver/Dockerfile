# Use the official Azure Functions Java base image (supports custom container)
FROM mcr.microsoft.com/azure-functions/java:4-java17

COPY target/azure-functions/* /home/site/wwwroot/

# Expose port (Function host listens on 80 inside the image)
EXPOSE 80
