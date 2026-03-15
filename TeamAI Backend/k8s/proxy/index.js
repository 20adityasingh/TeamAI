const http = require('http');
const httpProxy = require('http-proxy');
const Redis = require('ioredis');

const redisUrl = process.env.REDIS_URL || 'redis://team-ai-redis:6379';

const redis = new Redis(
    redisUrl,  {
        maxRetriesPerRequest: null,
        enableReadyCheck: false,
        retryStrategy(times){
            const delay = Math.min(times * 50, 2000);
            console.log(`Retrying connection to Redis in ${delay}ms...`);
            return delay;
        }
    }
);

redis.on('error', (err) => {
    console.error('Redis connection error:', err);
});

redis.on('ready', () => {
    console.log('Redis connection ready');
});

const proxy = httpProxy.createProxyServer({
    ws: true,
    xfwd: true,
    changeOrigin: true
});

async function getTarget(hostname){
    try {
        const targetIp = await redis.get(`route:${hostname}`);
        if(targetIp){
            return targetIp
        }
    } catch (error) {
        console.error('Error getting target:', error);
    }
    return null;
} 

const getTargetUrl = (target) => {
    return ip.includes (':') ? `http://${target}` : `http://${target}:5173`
}

const server = http.createServer(async (req, res) => {
    
    const rawHost = req.headers.host || '';
    const hostname = rawHost.split(':')[0];

    const targetIp = await getTarget(hostname);

    if(!targetIp){
        res.writeHead(404, {'Content-Type' : 'text/plain'});
        return res.end(`Preview not found for ${hostname}.`);
    }

    const target = getTargetUrl(targetIpOrSvc);
    console.log(`HTTP : ${hostname} -> ${target}${req.url}`);

    proxy.web(req, res, { target }, (e) => {
        console.error(`Proxy Error (Web): ${hostname}`, e.message);
        if(!res.headersSent){
            res.writeHead(502);
            res.end('Vite server unavailable...');
        }
    });

});


server.on('upgrade', (req, socket, head) => {
    const rawHost = req.headers.host || '';
    const hostname = rawHost.split(':')[0];

    getTarget(hostname).then(targetIpOrSvc => {
        if(targetIpOrSvc){
            const target = getTargetUrl(targetIpOrSvc);
            console.log(`WS: ${hostname} -> ${target}${req.url}`);
            proxy.ws(req, socket, head, { target }, (e) => {
                console.error(`Proxy Error (WS): ${hostname}`, e.message);
                socket.destroy();
            });
        }else{
            socket.destroy();
        }
    }).catch(err => {
        console.error(`Proxy Error (WS): ${hostname}`, err.message);
        socket.destroy();
    });
});

server.listen(80, () => {
    console.log(`Proxy server running on port 80`);
});
