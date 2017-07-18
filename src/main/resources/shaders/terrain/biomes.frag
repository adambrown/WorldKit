#version 330

uniform sampler2D biomeTexture;
uniform sampler2D splineTexture;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    int id = int(texture(biomeTexture, VertexIn.uv).r * 256);
    vec2 splineColor = texture(splineTexture, VertexIn.uv).rg;
    float alpha = splineColor.g;
    float iAlpha = 1.0 - alpha;

    vec4 color;
    switch (id) {
        case 1: color = vec4(0.28627452f, 0.93333334f, 0.28627452f, 1.0f); break;
        case 2: color = vec4(1.0f, 0.6392157f, 0.9411765f, 1.0f); break;
        case 3: color = vec4(1.0f, 1.0f, 0.5294118f, 1.0f); break;
        case 4: color = vec4(0.627451f, 0.24313726f, 0.8901961f, 1.0f); break;
        case 5: color = vec4(0.8392157f, 0.53333336f, 0.21960784f, 1.0f); break;
        case 6: color = vec4(0.41568628f, 0.41568628f, 1.0f, 1.0f); break;
        case 7: color = vec4(0.9647059f, 0.34509805f, 0.34509805f, 1.0f); break;
        case 8: color = vec4(0.1882353f, 0.8039216f, 0.80784315f, 1.0f); break;
        case 9: color = vec4(0.17254902f, 0.57254905f, 0.17254902f, 1.0f); break;
        case 10: color = vec4(0.7607843f, 0.16862746f, 0.65882355f, 1.0f); break;
        case 11: color = vec4(0.69803923f, 0.7019608f, 0.1764706f, 1.0f); break;
        case 12: color = vec4(0.38431373f, 0.18039216f, 0.5372549f, 1.0f); break;
        case 13: color = vec4(0.49411765f, 0.34117648f, 0.19215687f, 1.0f); break;
        case 14: color = vec4(0.16470589f, 0.16470589f, 0.6627451f, 1.0f); break;
        case 15: color = vec4(0.61960787f, 0.16078432f, 0.16078432f, 1.0f); break;
        case 16: color = vec4(0.20392157f, 0.45490196f, 0.45490196f, 1.0f); break;
        default: color = vec4(0.0, 0.0, 0.0, 1.0); break;
    }
    colorOut = vec4((color.rgb * iAlpha + splineColor.rrr * alpha), 1.0);
}
