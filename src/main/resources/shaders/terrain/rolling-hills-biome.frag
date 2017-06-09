#version 330

uniform sampler2D regionBorderDistanceMask;
uniform sampler2D coastDistanceMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float regionBorderDistance = texture(regionBorderDistanceMask, VertexIn.uv).r;
    if (regionBorderDistance > 0.995) {
        float height = ((0.005 - (regionBorderDistance - 0.995)) * 8) + 0.00000015;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
        if (coastDistance > 0.999) {
            colorOut = vec4(0.01, 0.01, 0.01, 1.0);
        } else {
            float height = texture(noiseMask1, VertexIn.uv).r;
            colorOut = vec4(height, height, height, 1.0);
        }
    }
}
