#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;

// Profundidad lineal aproximada (near 0.05, far 256)
float linearDepth(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    float near = 0.05;
    float far = 256.0;
    return (2.0 * near * far) / (far + near - (d * 2.0 - 1.0) * (far - near));
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 px = 1.0 / InSize;

    // Profundidad del centro y vecinos
    float dC = linearDepth(texCoord);
    float dL = linearDepth(texCoord + vec2(-px.x, 0.0));
    float dR = linearDepth(texCoord + vec2( px.x, 0.0));
    float dU = linearDepth(texCoord + vec2(0.0,  px.y));
    float dD = linearDepth(texCoord + vec2(0.0, -px.y));

    // Borde por salto de profundidad (relativo, para que funcione de cerca y de lejos)
    float depthEdge = (abs(dC - dL) + abs(dC - dR) + abs(dC - dU) + abs(dC - dD)) / max(dC, 0.1);
    depthEdge = smoothstep(0.04, 0.12, depthEdge);

    // Borde por cambio de color (detalles dentro de una misma superficie)
    float cC = luma(texture(DiffuseSampler, texCoord).rgb);
    float cL = luma(texture(DiffuseSampler, texCoord + vec2(-px.x, 0.0)).rgb);
    float cR = luma(texture(DiffuseSampler, texCoord + vec2( px.x, 0.0)).rgb);
    float cU = luma(texture(DiffuseSampler, texCoord + vec2(0.0,  px.y)).rgb);
    float cD = luma(texture(DiffuseSampler, texCoord + vec2(0.0, -px.y)).rgb);
    float colorEdge = smoothstep(0.10, 0.30, abs(cC - cL) + abs(cC - cR) + abs(cC - cU) + abs(cC - cD));

    float edge = clamp(depthEdge + colorEdge * 0.6, 0.0, 1.0);

    // Fondo oscuro con un toque violeta y las siluetas en magenta
    vec3 background = vec3(0.02, 0.0, 0.05) + texture(DiffuseSampler, texCoord).rgb * 0.06;
    vec3 outline = vec3(1.0, 0.25, 0.9);

    fragColor = vec4(mix(background, outline, edge), 1.0);
}
