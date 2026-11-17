export default {
  async fetch(request, env) {
    const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type' };
    if (request.method === 'OPTIONS') return new Response(null, { headers: cors });
    if (!env.AGNES_API_KEY) return Response.json({ error: 'AGNES_API_KEY sunucu secret ayarı eksik.' }, { status: 503, headers: cors });
    const url = new URL(request.url);
    const headers = { Authorization: 'Bearer ' + env.AGNES_API_KEY, 'Content-Type': 'application/json' };
    try {
      if (url.pathname === '/generate' && request.method === 'POST') {
        const body = await request.json();
        if (!String(body.prompt || '').trim()) return Response.json({ error: 'prompt zorunludur.' }, { status: 400, headers: cors });
        const payload = { model: 'agnes-video-v2.0', prompt: String(body.prompt).slice(0, 4000), width: 720, height: 1280, num_frames: 121, frame_rate: 24 };
        if (body.image) payload.image = String(body.image);
        const upstream = await fetch('https://apihub.agnes-ai.com/v1/videos', { method: 'POST', headers, body: JSON.stringify(payload) });
        return Response.json(await upstream.json(), { status: upstream.status, headers: cors });
      }
      if (url.pathname === '/status' && request.method === 'GET') {
        const videoId = url.searchParams.get('video_id');
        if (!videoId) return Response.json({ error: 'video_id zorunludur.' }, { status: 400, headers: cors });
        const target = new URL('https://apihub.agnes-ai.com/agnesapi');
        target.searchParams.set('video_id', videoId);
        target.searchParams.set('model_name', 'agnes-video-v2.0');
        const upstream = await fetch(target, { headers });
        return Response.json(await upstream.json(), { status: upstream.status, headers: cors });
      }
      return Response.json({ error: 'Geçersiz yol.' }, { status: 404, headers: cors });
    } catch (error) {
      return Response.json({ error: 'Video servisine ulaşılamadı.', detail: String(error) }, { status: 502, headers: cors });
    }
  }
}
