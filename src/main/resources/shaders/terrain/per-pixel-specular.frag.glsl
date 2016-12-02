#version 410

uniform vec4 color;
uniform vec4 diffuseColor;
uniform vec4 specularColor;
uniform float shininess;

in VertexData {
    vec3 lightVector;
    vec3 normal;
    vec3 eye;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    vec4 spec = vec4(0.0);
    vec3 l = normalize(VertexIn.lightVector);
    vec3 n;
    if (gl_FrontFacing) {
        n = normalize(VertexIn.normal);
    } else {
        n = normalize(-VertexIn.normal);
    }
    vec3 e = normalize(VertexIn.eye);
    float intensity = max(dot(n,l), 0.0);
    if (intensity > 0.0) {
        vec3 h = normalize(l + e);
        float intSpec = max(dot(h,n), 0.0);
        spec = specularColor * pow(intSpec, shininess);
    }
    colorOut = max(intensity * diffuseColor + spec, color);
}