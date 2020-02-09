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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

/**
 * Adds the maven-wrapper files to this project 
 * 
 * @author Robert Scholte
 * @since 3.0.0
 */
@Mojo( name = "wrapper", requiresProject = false, aggregator = true )
public class WrapperMojo extends AbstractMojo
{
    // CONFIGURATION PARAMETERS
    
    /**
     * The version of Maven to require, default value is the Runtime version of Maven
     */
    @Parameter
    private String mavenVersion;
    
    /**
     * Options are:
     * <dl>
     *   <dt>bin (default)</dt>
     *   <dd>precompiled and packaged code</dd>
     *   <dt>java</dt>
     *   <dd>sourcecode, will be compiled on the fly</dd>
     * </dl> 
     * 
     * Value will be used as classifier of the downloaded file
     */
    @Parameter( defaultValue = "bin" )
    private String distributionType;

    // READONLY PARAMETERS 
    
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Parameter( defaultValue = "org.apache.maven", readonly = true, required = true )
    private String groupId;

    @Parameter( defaultValue = "maven-wrapper", readonly = true, required = true )
    private String artifactId;

    @Parameter( defaultValue = "tar.gz", readonly = true, required = true )
    private String extension;
    
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
        
        Path targetFolder = Paths.get( ".mvn/wrapper" );

        try
        {
            unpack( artifact, targetFolder );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        
        update( targetFolder );
    }
    
    private Artifact download()
        throws ArtifactResolverException
    {
        ProjectBuildingRequest buildingRequest =
            new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId( groupId );
        coordinate.setArtifactId( artifactId );
        coordinate.setVersion( getVersion() ); // 
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
        unarchiver.extract();
    }
    
    private void update( Path targetFolder ) 
    {
        // update wrapper.properties
    }
    
    private String getVersion()
    {
        String version = this.mavenVersion;
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
