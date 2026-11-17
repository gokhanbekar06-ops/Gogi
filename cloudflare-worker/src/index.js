export default {
  async fetch(request, env) {
    const cors = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Content-Type': 'application/json; charset=utf-8'
    };
    if (request.method === 'OPTIONS') return new Response(null, { headers: cors });
    if (!env.AGNES_API_KEY) return Response.json({ error: 'AGNES_API_KEY sunucu secret ayarı eksik.' }, { status: 503, headers: cors });
    const url = new URL(request.url);
    const headers = { Authorization: 'Bearer ' + env.AGNES_API_KEY, 'Content-Type': 'application/json' };
    const model = 'agnes-video-2.5-flash';

    async function readUpstream(response) {
      const raw = await response.text();
      let data;
      try { data = JSON.parse(raw); }
      catch {
        const limited = /1015|rate.?limit|too many requests/i.test(raw);
        return {
          status: limited ? 429 : (response.status >= 400 ? response.status : 502),
          body: {
            error: limited
              ? 'Agnes video servisi istek sınırına ulaştı. Lütfen biraz bekleyip tekrar deneyin.'
              : 'Agnes video servisi geçerli JSON yanıtı vermedi.',
            upstream_status: response.status,
            detail: raw.slice(0, 500),
            retry_after_seconds: limited ? 65 : undefined
          }
        };
      }
      const limited = response.status === 429 || /1015|rate.?limit/i.test(JSON.stringify(data));
      return {
        status: limited ? 429 : response.status,
        body: limited ? { ...data, error: data.error || 'Agnes video servisi istek sınırına ulaştı.', retry_after_seconds: 65 } : data
      };
    }

    try {
      if (url.pathname === '/generate' && request.method === 'POST') {
        const body = await request.json();
        if (!String(body.prompt || '').trim()) return Response.json({ error: 'prompt zorunludur.' }, { status: 400, headers: cors });
        const payload = {
          model,
          prompt: String(body.prompt).slice(0, 4000),
          mode: body.image ? 'img2video' : 'text',
          seconds: Math.min(12, Math.max(4, Number(body.seconds) || 5)),
          size: '720P',
          aspect_ratio: '9:16'
        };
        if (body.image) payload.first_frame = String(body.image);
        const upstream = await fetch('https://apihub.agnes-ai.com/v1/videos', {
          method: 'POST', headers, body: JSON.stringify(payload)
        });
        const result = await readUpstream(upstream);
        return Response.json(result.body, { status: result.status, headers: cors });
      }

      if (url.pathname === '/status' && request.method === 'GET') {
        const videoId = url.searchParams.get('video_id');
        if (!videoId) return Response.json({ error: 'video_id zorunludur.' }, { status: 400, headers: cors });
        const target = new URL('https://apihub.agnes-ai.com/agnesapi');
        target.searchParams.set('video_id', videoId);
        target.searchParams.set('model_name', model);
        const upstream = await fetch(target, { headers });
        const result = await readUpstream(upstream);
        return Response.json(result.body, { status: result.status, headers: cors });
      }

      return Response.json({ error: 'Geçersiz yol.' }, { status: 404, headers: cors });
    } catch (error) {
      return Response.json({ error: 'Video servisine ulaşılamadı.', detail: String(error) }, { status: 502, headers: cors });
    }
  }
}
