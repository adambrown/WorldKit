#version 410

uniform vec4 lightPosition;
uniform vec4 color;

in VertexData {
    vec3 position;
    vec3 normal;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    vec3 lightDir = vec3(lightPosition) - VertexIn.position;
    float ligthDistance = length(lightDir);
    vec3 lightVector = normalize(lightDir);
    float diffuse;

    if (gl_FrontFacing) {
        diffuse = max(dot(VertexIn.normal, lightVector), 0.0);
    } else {
        diffuse = max(dot(-VertexIn.normal, lightVector), 0.0);
    }
    diffuse = diffuse * (1.0 / (1.0 + (0.02 * ligthDistance)));
    diffuse = diffuse + 0.15;
    colorOut = (color * diffuse);
}

