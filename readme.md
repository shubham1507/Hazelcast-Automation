for /F "tokens=*" %i in ('docker ps -q') do docker stop %i
for /F "tokens=*" %i in ('docker ps -aq') do docker rm %i
for /F "tokens=*" %i in ('docker images -q') do docker rmi -f %i
for /F "tokens=*" %i in ('docker volume ls -q') do docker volume rm %i
docker network prune -f
docker system prune -a --volumes -f

