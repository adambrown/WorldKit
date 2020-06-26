#version 330

uniform float textureSize;
uniform float gaussMultiplier;
uniform sampler2D vertexPositions;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

float uvSize = 1.0 / textureSize;
float scale = textureSize * gaussMultiplier;

vec2 compute(vec3 vert, vec2 hc) {
    vec2 offset = (VertexIn.uv - vert.xy) * scale;
    float d2 = dot(offset, offset);
    float coeff = 0.56418958354 * pow(2.7182818284590452354, -d2);
    return vec2(hc.x + vert.z * coeff, hc.y + coeff);
}

void main() {
    vec2 adjustedUv = round(VertexIn.uv * textureSize) * uvSize;
    vec2 hc = vec2(0.0, 0.0);
    for (int uOff = -5; uOff < 6; uOff++) {
        for (int vOff = -5; vOff < 6; vOff++) {
            hc = compute(texture(vertexPositions, adjustedUv + vec2(uvSize * uOff, uvSize * vOff)).rgb, hc);
        }
    }
    float height = hc.x / hc.y;
    colorOut = vec4(height, height, height, 1.0);
}
