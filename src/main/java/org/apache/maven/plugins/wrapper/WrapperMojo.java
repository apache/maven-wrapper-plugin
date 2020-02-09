package org.apache.maven.plugins.wrapper;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;

/**
 * Adds the maven-wrapper files to this project 
 * 
 * @author Robert Scholte
 * @since 3.0.0
 */
@Mojo( name = "wrapper", aggregator = true, requiresDirectInvocation = true )
public class WrapperMojo extends AbstractMojo
{
    // CONFIGURATION PARAMETERS
    
    /**
     * The version of Maven to require, default value is the Runtime version of Maven. 
     * Can be any valid release above 2.0.9
     */
    @Parameter( property = "mavenVersion" )
    private String mavenVersion;

    /**
     * The version of the wrapper, default value is the Runtime version of Maven, should be at least 3.7.0
     */
    @Parameter( property = "wrapperVersion" )
    private String wrapperVersion;

    /**
     * Options are:
     * <dl>
     *   <dt>script (default)</dt>
     *   <dd>only mvnw scripts</dd>
     *   <dt>bin</dt>
     *   <dd>precompiled and packaged code</dd>
     *   <dt>source</dt>
     *   <dd>sourcecode, will be compiled on the fly</dd>
     * </dl> 
     * 
     * Value will be used as classifier of the downloaded file
     */
    @Parameter( defaultValue = "script", property = "type" )
    private String distributionType;

    @Parameter( defaultValue = "true", property = "includeDebug" )
    private boolean includeDebugScript;
    
    // READONLY PARAMETERS 
    
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;
    
    // Waiting for org.codehaus.plexus.component.configurator.converters.basic.PathConverter
    @Parameter( defaultValue = "${project.basedir}" )
    private File basedir;

    // CONSTANTS

    private String groupId = "org.apache.maven";

    private String artifactId = "apache-maven-wrapper";

    private String extension = "zip";
    
    // COMPONENTS 
    
    @Inject
    private ArtifactResolver artifactResolver;
    
    @Inject
    private Map<String, UnArchiver> unarchivers;
    
    @Override
    public void execute() throws MojoExecutionException
    {
        Artifact artifact;
        try
        {
            artifact = download();
        }
        catch ( ArtifactResolverException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        
        try
        {
            unpack( artifact, basedir.toPath() );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        
        if ( mavenVersion != null )
        {
            try
            {
                replaceProperties( Files.createDirectories( basedir.toPath().resolve( ".mvn/wrapper" ) ) );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "can't create wrapper.proeprties", e );
            }
        }
    }
    
    private Artifact download()
        throws ArtifactResolverException
    {
        ProjectBuildingRequest buildingRequest =
            new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId( groupId );
        coordinate.setArtifactId( artifactId );
        coordinate.setVersion( getVersion( this.wrapperVersion ) ); 
        coordinate.setClassifier( distributionType );
        coordinate.setExtension( extension );

        return artifactResolver.resolveArtifact( buildingRequest, coordinate ).getArtifact();
    }
    
    private void unpack( Artifact artifact, Path targetFolder ) throws IOException 
    {
        targetFolder = Files.createDirectories( targetFolder );
        UnArchiver unarchiver = unarchivers.get( extension );
        unarchiver.setDestDirectory( targetFolder.toFile() );
        unarchiver.setSourceFile( artifact.getFile() );
        if ( !includeDebugScript )
        {
            unarchiver.setFileSelectors( new FileSelector[] 
                            {
                                f -> !f.getName().contains( "Debug" ) 
                            } );
        }
        unarchiver.extract();
    }
    
    /**
     * As long as the content only contains the license and the distributionUrl, we can simply replace it.
     * No need to look for other properties, restore them, respecting comments, etc.
     * 
     * @param targetFolder the folder containing the wrapper.properties
     * @throws IOException if writing fails
     */
    private void replaceProperties( Path targetFolder ) throws IOException
    {
        Path wrapperPropertiesFile = targetFolder.resolve( "maven-wrapper.properties" );
        
        final String license = "# Licensed to the Apache Software Foundation (ASF) under one\n"
            + "# or more contributor license agreements.  See the NOTICE file\n"
            + "# distributed with this work for additional information\n"
            + "# regarding copyright ownership.  The ASF licenses this file\n"
            + "# to you under the Apache License, Version 2.0 (the\n"
            + "# \"License\"); you may not use this file except in compliance\n"
            + "# with the License.  You may obtain a copy of the License at\n" + "# \n"
            + "#   http://www.apache.org/licenses/LICENSE-2.0\n" + "# \n"
            + "# Unless required by applicable law or agreed to in writing,\n"
            + "# software distributed under the License is distributed on an\n"
            + "# \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY\n"
            + "# KIND, either express or implied.  See the License for the\n"
            + "# specific language governing permissions and limitations\n" + "# under the License.\n";
        
        try ( BufferedWriter out = Files.newBufferedWriter( wrapperPropertiesFile ) )
        {
            out.append( license );
            out.append( "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/"
                + mavenVersion
                + "/apache-maven-"
                + mavenVersion
                + "-bin.zip" );
        }
    }
    
    private String getVersion( String defaultVersion )
    {
        String version = defaultVersion;
        if ( version == null )
        {
            Properties props = new Properties();
            try ( InputStream is =
                Maven.class.getResourceAsStream( "/META-INF/maven/org.apache.maven/maven-core/pom.properties" ) )
            {
                props.load( is );
                version = props.getProperty( "version" );
            }
            catch ( IOException e )
            {
                // noop
            }
        }
        return version;
    }
}
