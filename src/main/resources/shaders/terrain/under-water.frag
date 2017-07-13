#version 330

uniform float textureScale;
uniform float borderDistanceScale;
uniform sampler2D coastDistanceMask;
uniform sampler2D landMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    bool isWater = texture(landMask, VertexIn.uv).r < 0.5;
    if (isWater) {
        float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
        float height = 1.0 - min(1.0, (1.0 - coastDistance * coastDistance) * 7 * borderDistanceScale);
        float noiseHeight = texture(noiseMask1, VertexIn.uv * textureScale * 0.6).r * 0.5;
        height = max(height, noiseHeight);
        colorOut = vec4(height, height, height, 1.0);
    } else {
        colorOut = vec4(1.0, 1.0, 1.0, 1.0);
    }
}
